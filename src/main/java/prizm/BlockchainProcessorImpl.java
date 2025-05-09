/** ****************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ***************************************************************************** */
package prizm;

import prizm.crypto.Crypto;
import prizm.db.DbIterator;
import prizm.db.DerivedDbTable;
import prizm.db.FilteringIterator;
import prizm.db.FullTextTrigger;
import prizm.peer.Peer;
import prizm.peer.Peers;
import prizm.util.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import org.json.simple.JSONValue;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

final class BlockchainProcessorImpl implements BlockchainProcessor {

    private static final byte[] CHECKSUM_TRANSPARENT_FORGING = Constants.isTestnet
            ? new byte[]{
                0
            }
            : new byte[]{
                -29, -80, -60, 66, -104, -4, 28, 20, -102, -5, -12, -56, -103, 111, -71, 36, 39, -82, 65, -28, 100, -101, -109, 76, -92, -107, -103, 27, 120, 82, -72, 85
            };
    private static final byte[] CHECKSUM_1 = Constants.isTestnet
            ? new byte[]{
                0
            }
            : new byte[]{
                -51, -47, -127, -36, 65, -8, 66, -9, -89, 24, 16, -109, 36, 63, 76, -75, 3, -101, 30, -23, 53, -119, -39, -56, 99, 100, -70, 106, -87, 99, 110, -65
            };
    private static final byte[] CHECKSUM_2 = Constants.isTestnet
            ? new byte[]{
                0
            }
            : new byte[]{
                99, 44, -128, 91, 57, -88, -30, -9, 103, -46, 73, 100, 76, 12, -21, 40, -109, 83, 101, 31, -34, 4, 117, -13, -108, -56, 0, -19, -52, 105, -10, -68
            };


    private static final BlockchainProcessorImpl instance = new BlockchainProcessorImpl();

    static BlockchainProcessorImpl getInstance() {
        return instance;
    }

    private final BlockchainImpl blockchain = BlockchainImpl.getInstance();

    private final ExecutorService networkService = Executors.newCachedThreadPool();
    private final List<DerivedDbTable> derivedTables = new CopyOnWriteArrayList<>();
    private final boolean trimDerivedTables = Prizm.getBooleanProperty("prizm.trimDerivedTables");
    private final int defaultNumberOfForkConfirmations = Prizm.getIntProperty(Constants.isTestnet
            ? "prizm.testnetNumberOfForkConfirmations" : "prizm.numberOfForkConfirmations");
    private final boolean simulateEndlessDownload = Prizm.getBooleanProperty("prizm.simulateEndlessDownload");

    private int initialScanHeight;
    private volatile int lastTrimHeight;
    private volatile int lastRestoreTime = 0;
    private final Set<Long> prunableTransactions = new HashSet<>();

    private final Listeners<Block, Event> blockListeners = new Listeners<>();
    private volatile Peer lastBlockchainFeeder;
    private volatile int lastBlockchainFeederHeight;
    private volatile boolean getMoreBlocks = true;

    private volatile boolean isTrimming;
    private volatile boolean isScanning;
    private volatile boolean isDownloading;
    private volatile boolean isProcessingBlock;
    private volatile boolean isRestoring;
    private volatile boolean alreadyInitialized = false;

    private final Runnable getMoreBlocksThread = new Runnable() {

        private final JSONStreamAware getCumulativeDifficultyRequest;

        {
            JSONObject request = new JSONObject();
            request.put("requestType", "getCumulativeDifficulty");
            getCumulativeDifficultyRequest = JSON.prepareRequest(request);
        }

        private boolean peerHasMore;
        private List<Peer> connectedPublicPeers;
        private List<Long> chainBlockIds;
        private long totalTime = 1;
        private int totalBlocks;

        @Override
        public void run() {
            try {
                //
                // Download blocks until we are up-to-date
                //
                if (Prizm.shouldPerformRescan()) {
                    Prizm.setPerformRescan(false);
                    Logger.logWarningMessage(" === Starting blockchain update - please wait");
                    Logger.logWarningMessage(" === Blockchain update is expected to be done in 30 - 60 minutes");
                    final long startTimeRescan = System.currentTimeMillis();
                    try {
                        setGetMoreBlocks(false);
                        scan(0, false);
                        long timeSpent = (System.currentTimeMillis() - startTimeRescan) / 1000;
                        Logger.logWarningMessage(" === SUCCESS === Blockchain update finished in " + (timeSpent/60) + " minutes " + (timeSpent%60) + " seconds");
                    } finally {
                        setGetMoreBlocks(true);
                    }
                }
                if (blockchain.getHeight() > Constants.BEGIN_ZEROBLOCK_FIX) {
                    if (!Prizm.para().isZeroblockFixed()) {
                        try {
                            setGetMoreBlocks(false);
                            Logger.logWarningMessage(" === Starting blockchain update - please wait");
                            popOffTo(Constants.BEGIN_ZEROBLOCK_FIX);
                            Prizm.para().zeroblockFixed();
                            Logger.logWarningMessage(" === Blockchain update finished");
                        } finally {
                            setGetMoreBlocks(true);
                        }
                    }
                }
                while (true) {
                    if (!getMoreBlocks) {
                        return;
                    }
                    if (Constants.limitBlockchainHeight) {
                        if (blockchain.getHeight() >= Constants.maxBlockchainHeight) {
                            getMoreBlocks = false;
                            isDownloading = false;
                            if (blockchain.getHeight() > Constants.maxBlockchainHeight)
                                Logger.logErrorMessage("Blockchain height "+blockchain.getHeight()+" is higher then the prizm.maxBlockchainHeight "+Constants.maxBlockchainHeight+" in configuration file. Blockchain download  stopped.");
                            else {
                                Logger.logWarningMessage("Reached max blockchain height: " + Constants.maxBlockchainHeight);
                            }
                            return;
                        }
                    }
                    int chainHeight = blockchain.getHeight();
                    downloadPeer();
                    if (blockchain.getHeight() == chainHeight) {
                        if (isDownloading && !simulateEndlessDownload) {
                            Logger.logMessage("Finished blockchain download");
                            isDownloading = false;
                        }
                        break;
                    }
                }
                //
                // Restore prunable data
                //
                int now = Prizm.getEpochTime();
                if (!isRestoring && !prunableTransactions.isEmpty() && now - lastRestoreTime > 60 * 60) {
                    isRestoring = true;
                    lastRestoreTime = now;
                    networkService.submit(new RestorePrunableDataTask());
                }
            } catch (InterruptedException e) {
                Logger.logDebugMessage("Blockchain download thread interrupted");
            } catch (Throwable t) {
                Logger.logErrorMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString(), t);
                System.exit(1);
            }
        }

        private void downloadPeer() throws InterruptedException {
            try {
                long startTime = System.currentTimeMillis();
                int numberOfForkConfirmations = blockchain.getHeight() > Constants.FIRST_FORK_BLOCK ?   /////ss Constants.ENFORCE_FULL_TRANSACTION_VALIDATION - 720
                      defaultNumberOfForkConfirmations : Math.min(1, defaultNumberOfForkConfirmations);
                connectedPublicPeers = Peers.getPublicPeers(Peer.State.CONNECTED, true);
                if (connectedPublicPeers.size() <= numberOfForkConfirmations) {
                   return;
                }
                peerHasMore = true;
                final Peer peer = Peers.getWeightedPeer(connectedPublicPeers);
                if (peer == null) {
                    return;
                }
                JSONObject response = peer.send(getCumulativeDifficultyRequest);
                if (response == null) {
                    return;
                }
                BigInteger curCumulativeDifficulty = blockchain.getLastBlock().getCumulativeDifficulty();
                String peerCumulativeDifficulty = (String) response.get("cumulativeDifficulty");
                if (peerCumulativeDifficulty == null) {
                    return;
                }
                BigInteger betterCumulativeDifficulty = new BigInteger(peerCumulativeDifficulty);
                if (betterCumulativeDifficulty.compareTo(curCumulativeDifficulty) < 0) {
                    return;
                }
                if (response.get("blockchainHeight") != null) {
                    lastBlockchainFeeder = peer;
                    lastBlockchainFeederHeight = ((Long) response.get("blockchainHeight")).intValue();
                }
                if (betterCumulativeDifficulty.equals(curCumulativeDifficulty)) {
                    return;
                }

                long commonMilestoneBlockId = Genesis.GENESIS_BLOCK_ID;

                if (blockchain.getLastBlock().getId() != Genesis.GENESIS_BLOCK_ID) {
                    commonMilestoneBlockId = getCommonMilestoneBlockId(peer);
                }
                if (commonMilestoneBlockId == 0 || !peerHasMore) {
                    return;
                }

                chainBlockIds = getBlockIdsAfterCommon(peer, commonMilestoneBlockId, false);
                if (chainBlockIds.size() < 2 || !peerHasMore) {
                    return;
                }


                final long commonBlockId = chainBlockIds.get(0);
                final Block commonBlock = blockchain.getBlock(commonBlockId);
                if (commonBlock == null || blockchain.getHeight() - commonBlock.getHeight() >= 720) {
                    if (commonBlock != null) {
                        Logger.logDebugMessage(peer + " advertised chain with better difficulty, but the last common block is at height " + commonBlock.getHeight());
                    }
                    return;
                }
                if (simulateEndlessDownload) {
                    isDownloading = true;
                    return;
                }
                if (!isDownloading && lastBlockchainFeederHeight - commonBlock.getHeight() > 10) {
                    Logger.logMessage("Blockchain download in progress");
                    isDownloading = true;
                }

                blockchain.updateLock();
                try {
                    if (betterCumulativeDifficulty.compareTo(blockchain.getLastBlock().getCumulativeDifficulty()) <= 0) {
                        return;
                    }
                    long lastBlockId = blockchain.getLastBlock().getId();
                    downloadBlockchain(peer, commonBlock, commonBlock.getHeight());
                    if (blockchain.getHeight() - commonBlock.getHeight() <= 10) {
                        return;
                    }

                    int confirmations = 0;
                    for (Peer otherPeer : connectedPublicPeers) {
                        if (confirmations >= numberOfForkConfirmations) {
                            break;
                        }
                        if (peer.getHost().equals(otherPeer.getHost())) {
                            continue;
                        }
                        chainBlockIds = getBlockIdsAfterCommon(otherPeer, commonBlockId, true);
                        if (chainBlockIds.isEmpty()) {
                            continue;
                        }
                        long otherPeerCommonBlockId = chainBlockIds.get(0);
                        if (otherPeerCommonBlockId == blockchain.getLastBlock().getId()) {
                            confirmations++;
                            continue;
                        }
                        Block otherPeerCommonBlock = blockchain.getBlock(otherPeerCommonBlockId);
                        if (blockchain.getHeight() - otherPeerCommonBlock.getHeight() >= 720) {
                            continue;
                        }
                        String otherPeerCumulativeDifficulty;
                        JSONObject otherPeerResponse = peer.send(getCumulativeDifficultyRequest);
                        if (otherPeerResponse == null || (otherPeerCumulativeDifficulty = (String) response.get("cumulativeDifficulty")) == null) {
                            continue;
                        }
                        if (new BigInteger(otherPeerCumulativeDifficulty).compareTo(blockchain.getLastBlock().getCumulativeDifficulty()) <= 0) {
                            continue;
                        }
                        Logger.logDebugMessage("Found a peer with better difficulty");
                        downloadBlockchain(otherPeer, otherPeerCommonBlock, commonBlock.getHeight());
                    }
                    Logger.logDebugMessage("Got " + confirmations + " confirmations");

                    if (blockchain.getLastBlock().getId() != lastBlockId) {
                        long time = System.currentTimeMillis() - startTime;
                        totalTime += time;
                        int numBlocks = blockchain.getHeight() - commonBlock.getHeight();
                        totalBlocks += numBlocks;
                        Logger.logMessage("Downloaded " + numBlocks + " blocks in "
                                + time / 1000 + " s, " + (totalBlocks * 1000) / totalTime + " per s, "
                                + totalTime * (lastBlockchainFeederHeight - blockchain.getHeight()) / ((long) totalBlocks * 1000 * 60) + " min left");
                    } else {
                        Logger.logDebugMessage("Did not accept peer's blocks, back to our own fork");
                    }
                } finally {
                    blockchain.updateUnlock();
                }
            } catch (PrizmException.StopException e) {
                Logger.logMessage("Blockchain download stopped: " + e.getMessage());
                throw new InterruptedException("Blockchain download stopped");
            } catch (Exception e) {
                Logger.logMessage("Error in blockchain download thread", e);
            }
        }

        private long getCommonMilestoneBlockId(Peer peer) {

            String lastMilestoneBlockId = null;

            while (true) {
                JSONObject milestoneBlockIdsRequest = new JSONObject();
                milestoneBlockIdsRequest.put("requestType", "getMilestoneBlockIds");
                if (lastMilestoneBlockId == null) {
                    milestoneBlockIdsRequest.put("lastBlockId", blockchain.getLastBlock().getStringId());
                } else {
                    milestoneBlockIdsRequest.put("lastMilestoneBlockId", lastMilestoneBlockId);
                }

                JSONObject response = peer.send(JSON.prepareRequest(milestoneBlockIdsRequest));
                if (response == null) {
                    return 0;
                }
                JSONArray milestoneBlockIds = (JSONArray) response.get("milestoneBlockIds");
                if (milestoneBlockIds == null) {
                    return 0;
                }
                if (milestoneBlockIds.isEmpty()) {
                    return Genesis.GENESIS_BLOCK_ID;
                }
                // prevent overloading with blockIds
                if (milestoneBlockIds.size() > 20) {
                    Logger.logDebugMessage("Obsolete or rogue peer " + peer.getHost() + " sends too many milestoneBlockIds, blacklisting");
                    peer.blacklist("Too many milestoneBlockIds");
                    return 0;
                }
                if (Boolean.TRUE.equals(response.get("last"))) {
                    peerHasMore = false;
                }
                for (Object milestoneBlockId : milestoneBlockIds) {
                    long blockId = Convert.parseUnsignedLong((String) milestoneBlockId);
                    if (BlockDb.hasBlock(blockId)) {
                        if (lastMilestoneBlockId == null && milestoneBlockIds.size() > 1) {
                            peerHasMore = false;
                        }
                        return blockId;
                    }
                    lastMilestoneBlockId = (String) milestoneBlockId;
                }
            }

        }

        private List<Long> getBlockIdsAfterCommon(final Peer peer, final long startBlockId, final boolean countFromStart) {
            long matchId = startBlockId;
            List<Long> blockList = new ArrayList<>(720);
            boolean matched = false;
            int limit = countFromStart ? 720 : 1440;
            while (true) {
                JSONObject request = new JSONObject();
                request.put("requestType", "getNextBlockIds");
                request.put("blockId", Long.toUnsignedString(matchId));
                request.put("limit", limit);
                JSONObject response = peer.send(JSON.prepareRequest(request));
                if (response == null) {
                    return Collections.emptyList();
                }
                JSONArray nextBlockIds = (JSONArray) response.get("nextBlockIds");
                if (nextBlockIds == null || nextBlockIds.size() == 0) {
                    break;
                }
                // prevent overloading with blockIds
                if (nextBlockIds.size() > limit) {
                    Logger.logDebugMessage("Obsolete or rogue peer " + peer.getHost() + " sends too many nextBlockIds, blacklisting");
                    peer.blacklist("Too many nextBlockIds");
                    return Collections.emptyList();
                }
                boolean matching = true;
                int count = 0;
                for (Object nextBlockId : nextBlockIds) {
                    long blockId = Convert.parseUnsignedLong((String) nextBlockId);
                    if (matching) {
                        if (BlockDb.hasBlock(blockId)) {
                            matchId = blockId;
                            matched = true;
                        } else {
                            blockList.add(matchId);
                            blockList.add(blockId);
                            matching = false;
                        }
                    } else {
                        blockList.add(blockId);
                        if (blockList.size() >= 720) {
                            break;
                        }
                    }
                    if (countFromStart && ++count >= 720) {
                        break;
                    }
                }
                if (!matching || countFromStart) {
                    break;
                }
            }
            if (blockList.isEmpty() && matched) {
                blockList.add(matchId);
            }
            return blockList;
        }

        /**
         * Download the block chain
         *
         * @param feederPeer Peer supplying the blocks list
         * @param commonBlock Common block
         * @throws InterruptedException Download interrupted
         */
        private void downloadBlockchain(final Peer feederPeer, final Block commonBlock, final int startHeight) throws InterruptedException {
            Map<Long, PeerBlock> blockMap = new HashMap<>();
            //
            // Break the download into multiple segments.  The first block in each segment
            // is the common block for that segment.
            //
            List<GetNextBlocks> getList = new ArrayList<>();
            int segSize = 36;
            int stop = chainBlockIds.size() - 1;
            for (int start = 0; start < stop; start += segSize) {
                getList.add(new GetNextBlocks(chainBlockIds, start, Math.min(start + segSize, stop)));
            }
            int nextPeerIndex = ThreadLocalRandom.current().nextInt(connectedPublicPeers.size());
            long maxResponseTime = 0;
            Peer slowestPeer = null;
            //
            // Issue the getNextBlocks requests and get the results.  We will repeat
            // a request if the peer didn't respond or returned a partial block list.
            // The download will be aborted if we are unable to get a segment after
            // retrying with different peers.
            //
            download:            
            while (!getList.isEmpty()) {
                //
                // Submit threads to issue 'getNextBlocks' requests.  The first segment
                // will always be sent to the feeder peer.  Subsequent segments will
                // be sent to the feeder peer if we failed trying to download the blocks
                // from another peer.  We will stop the download and process any pending
                // blocks if we are unable to download a segment from the feeder peer.
                //
                for (GetNextBlocks nextBlocks : getList) {
                    Peer peer;
                    if (nextBlocks.getRequestCount() > 1) {
                       break download;
                    }
                    if (nextBlocks.getStart() == 0 || nextBlocks.getRequestCount() != 0) {
                        peer = feederPeer;
                    } else {
                        if (nextPeerIndex >= connectedPublicPeers.size()) {
                            nextPeerIndex = 0;
                        }
                        peer = connectedPublicPeers.get(nextPeerIndex++);
                    }
                    if (nextBlocks.getPeer() == peer) {
                        break download;
                    }
                    nextBlocks.setPeer(peer);
                    Future<List<BlockImpl>> future = networkService.submit(nextBlocks);
                    nextBlocks.setFuture(future);
                }
                //
                // Get the results.  A peer is on a different fork if a returned
                // block is not in the block identifier list.
                //
                Iterator<GetNextBlocks> it = getList.iterator();
                while (it.hasNext()) {
                    GetNextBlocks nextBlocks = it.next();
                    List<BlockImpl> blockList;
                    try {
                        blockList = nextBlocks.getFuture().get();
                    } catch (ExecutionException exc) {
                        throw new RuntimeException(exc.getMessage(), exc);
                    }
                    if (blockList == null) {
                        nextBlocks.getPeer().deactivate();
                        continue;
                    }
                    Peer peer = nextBlocks.getPeer();
                    int index = nextBlocks.getStart() + 1;
                    for (BlockImpl block : blockList) {
                        if (block.getId() != chainBlockIds.get(index)) {
                            break;
                        }

                        blockMap.put(block.getId(), new PeerBlock(peer, block));
                        index++;
                    }
                    if (index > nextBlocks.getStop()) {
                        it.remove();
                    } else {
                        nextBlocks.setStart(index - 1);
                    }
                    if (nextBlocks.getResponseTime() > maxResponseTime) {
                        maxResponseTime = nextBlocks.getResponseTime();
                        slowestPeer = nextBlocks.getPeer();
                    }
                }

            }
            if (slowestPeer != null && connectedPublicPeers.size() >= Peers.maxNumberOfConnectedPublicPeers && chainBlockIds.size() > 360) {
                Logger.logDebugMessage(slowestPeer.getHost() + " took " + maxResponseTime + " ms, disconnecting");
                slowestPeer.deactivate();
            }
            //
            // Add the new blocks to the blockchain.  We will stop if we encounter
            // a missing block (this will happen if an invalid block is encountered
            // when downloading the blocks)
            //
            blockchain.writeLock();
            try {
                List<BlockImpl> forkBlocks = new ArrayList<>();
                for (int index = 1; index < chainBlockIds.size() && blockchain.getHeight() - startHeight < 720; index++) {
                    PeerBlock peerBlock = blockMap.get(chainBlockIds.get(index));
                    if (peerBlock == null) {
                        break;
                    }
                    BlockImpl block = peerBlock.getBlock();
                    if (blockchain.getLastBlock().getId() == block.getPreviousBlockId()) {
                        try {
                            // Peer blocks
                            pushBlock(block);
                        } catch (BlockNotAcceptedException e) {
                            peerBlock.getPeer().blacklist(e);
                        }
                    } else {
                        forkBlocks.add(block);
                    }
                }
                //
                // Process a fork
                //
                int myForkSize = blockchain.getHeight() - startHeight;
                if (!forkBlocks.isEmpty() && myForkSize < 720) {
                    if (prevalidateFork(forkBlocks)) {
                        if (Prizm.isCompatiblePeerVersion(feederPeer.getVersion())) {
                            Logger.logDebugMessage("Will process a fork of " + forkBlocks.size() + " blocks, mine is " + myForkSize);
                            processFork(feederPeer, forkBlocks, commonBlock);
                        } else {
                            Logger.logDebugMessage("Refusing fork from incompatible peer " + feederPeer.getHost() + " (version " + feederPeer.getVersion() + "), first fork block is " + forkBlocks.get(0).getStringId() + " at height " + (BlockchainImpl.getInstance().getBlock(forkBlocks.get(0).getPreviousBlockId()).getHeight()+1));
                            feederPeer.blacklist("Peer is incompatible");
                        }
                    } else {
                        feederPeer.blacklist("Peer is sending bad blocks");
                    }
                }
            } finally {
                blockchain.writeUnlock();
            }

        }

        private boolean prevalidateFork(List<BlockImpl> forkBlocks) {
            if (blockchain.getHeight() >= Constants.ENABLE_BLOCK_VERSION_PREVALIDATION) {
                Set<String> blacklistedBlockIds = Constants.getBlacklistedBlocks();
                long previousBlockId = forkBlocks.get(0).getPreviousBlockId();
                BlockImpl previousBlock = BlockchainImpl.getInstance().getBlock(previousBlockId);
                if (previousBlock != null) {
                    int lastHeight = previousBlock.getHeight();
                    for (int i = 0; i < forkBlocks.size(); i++) {
                        Block forkBlock = forkBlocks.get(i);
                        int forkBlockHeight = lastHeight + (i+1);
                        if (forkBlock.getVersion() != getBlockVersion(forkBlockHeight-1)) {
                            Logger.logWarningMessage("Refusing peer blocks with invalid block versioning (first block is " + forkBlocks.get(0).getId() + " at " + (lastHeight+1) + ", bad versioned block is " + forkBlock.getId() + " at " + (forkBlockHeight) + ")");
                            return false;
                        }
                        if (blacklistedBlockIds.contains(forkBlock.getStringId())) {
                            Logger.logWarningMessage("Refusing peer blocks containing bad block (first fork block is " + forkBlocks.get(0).getId() + " at " + (lastHeight+1) + ", bad block is " + forkBlock.getId() + " at " + (forkBlockHeight) + ")");
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        private void processFork(final Peer peer, final List<BlockImpl> forkBlocks, final Block commonBlock) {
            BigInteger curCumulativeDifficulty = blockchain.getLastBlock().getCumulativeDifficulty();

            List<BlockImpl> myPoppedOffBlocks = popOffTo(commonBlock);

            int pushedForkBlocks = 0;
            if (blockchain.getLastBlock().getId() == commonBlock.getId()) {
                for (BlockImpl block : forkBlocks) {
                    if (blockchain.getLastBlock().getId() == block.getPreviousBlockId()) {
                        try {
                            pushBlock(block);
                            pushedForkBlocks += 1;
                        } catch (BlockNotAcceptedException e) {
                            peer.blacklist(e);
                            break;
                        }
                    }
                }
            }

            if (pushedForkBlocks > 0 && blockchain.getLastBlock().getCumulativeDifficulty().compareTo(curCumulativeDifficulty) < 0) {
                Logger.logDebugMessage("Pop off caused by peer " + peer.getHost() + ", blacklisting");
                peer.blacklist("Pop off");
                List<BlockImpl> peerPoppedOffBlocks = popOffTo(commonBlock);
                pushedForkBlocks = 0;
                for (BlockImpl block : peerPoppedOffBlocks) {
                    TransactionProcessorImpl.getInstance().processLater(block.getTransactions());
                }
            }

            if (pushedForkBlocks == 0) {
                Logger.logDebugMessage("Didn't accept any blocks, pushing back my previous blocks");
                for (int i = myPoppedOffBlocks.size() - 1; i >= 0; i--) {
                    BlockImpl block = myPoppedOffBlocks.remove(i);
                    try {
                        pushBlock(block);
                    } catch (BlockNotAcceptedException e) {
                        Logger.logErrorMessage("Popped off block no longer acceptable: " + block.getJSONObject().toJSONString(), e);
                        break;
                    }
                }
            } else {
                Logger.logDebugMessage("Switched to peer's fork");
                for (BlockImpl block : myPoppedOffBlocks) {
                    TransactionProcessorImpl.getInstance().processLater(block.getTransactions());
                }
            }

        }

    };

    /**
     * Callable method to get the next block segment from the selected peer
     */
    private static class GetNextBlocks implements Callable<List<BlockImpl>> {

        /**
         * Callable future
         */
        private Future<List<BlockImpl>> future;

        /**
         * Peer
         */
        private Peer peer;

        /**
         * Block identifier list
         */
        private final List<Long> blockIds;

        /**
         * Start index
         */
        private int start;

        /**
         * Stop index
         */
        private int stop;

        /**
         * Request count
         */
        private int requestCount;

        /**
         * Time it took to return getNextBlocks
         */
        private long responseTime;

        /**
         * Create the callable future
         *
         * @param blockIds Block identifier list
         * @param start Start index within the list
         * @param stop Stop index within the list
         */
        public GetNextBlocks(List<Long> blockIds, int start, int stop) {
            this.blockIds = blockIds;
            this.start = start;
            this.stop = stop;
            this.requestCount = 0;
        }

        /**
         * Return the result
         *
         * @return List of blocks or null if an error occurred
         */
        @Override
        public List<BlockImpl> call() {
            requestCount++;
            //
            // Build the block request list
            //
            JSONArray idList = new JSONArray();
            for (int i = start + 1; i <= stop; i++) {
                idList.add(Long.toUnsignedString(blockIds.get(i)));
            }
            JSONObject request = new JSONObject();
            request.put("requestType", "getNextBlocks");
            request.put("blockIds", idList);
            request.put("blockId", Long.toUnsignedString(blockIds.get(start)));
            long startTime = System.currentTimeMillis();
            JSONObject response = peer.send(JSON.prepareRequest(request), Peers.MAX_MESSAGE_SIZE);
            responseTime = System.currentTimeMillis() - startTime;
            if (response == null) {
                return null;
            }
            //
            // Get the list of blocks.  We will stop parsing blocks if we encounter
            // an invalid block.  We will return the valid blocks and reset the stop
            // index so no more blocks will be processed.
            //
            List<JSONObject> nextBlocks = (List<JSONObject>) response.get("nextBlocks");
            if (nextBlocks == null)
                return null;
            if (nextBlocks.size() > 36) {
                Logger.logDebugMessage("Obsolete or rogue peer " + peer.getHost() + " sends too many nextBlocks, blacklisting");
                peer.blacklist("Too many nextBlocks");
                return null;
            }
            List<BlockImpl> blockList = new ArrayList<>(nextBlocks.size());
            try {
                int count = stop - start;
                for (JSONObject blockData : nextBlocks) {
                    blockList.add(BlockImpl.parseBlock(blockData));
                    if (--count <= 0)
                        break;
                }
            } catch (RuntimeException | PrizmException.NotValidException e) {
                Logger.logDebugMessage("Failed to parse block: " + e.toString(), e);
                peer.blacklist(e);
                stop = start + blockList.size();
            }
            return blockList;
        }

        /**
         * Return the callable future
         *
         * @return Callable future
         */
        public Future<List<BlockImpl>> getFuture() {
            return future;
        }

        /**
         * Set the callable future
         *
         * @param future Callable future
         */
        public void setFuture(Future<List<BlockImpl>> future) {
            this.future = future;
        }

        /**
         * Return the peer
         *
         * @return Peer
         */
        public Peer getPeer() {
            return peer;
        }

        /**
         * Set the peer
         *
         * @param peer Peer
         */
        public void setPeer(Peer peer) {
            this.peer = peer;
        }

        /**
         * Return the start index
         *
         * @return Start index
         */
        public int getStart() {
            return start;
        }

        /**
         * Set the start index
         *
         * @param start Start index
         */
        public void setStart(int start) {
            this.start = start;
        }

        /**
         * Return the stop index
         *
         * @return Stop index
         */
        public int getStop() {
            return stop;
        }

        /**
         * Return the request count
         *
         * @return Request count
         */
        public int getRequestCount() {
            return requestCount;
        }

        /**
         * Return the response time
         *
         * @return Response time
         */
        public long getResponseTime() {
            return responseTime;
        }
    }

    /**
     * Block returned by a peer
     */
    private static class PeerBlock {

        /**
         * Peer
         */
        private final Peer peer;

        /**
         * Block
         */
        private final BlockImpl block;

        /**
         * Create the peer block
         *
         * @param peer Peer
         * @param block Block
         */
        public PeerBlock(Peer peer, BlockImpl block) {
            this.peer = peer;
            this.block = block;
        }

        /**
         * Return the peer
         *
         * @return Peer
         */
        public Peer getPeer() {
            return peer;
        }

        /**
         * Return the block
         *
         * @return Block
         */
        public BlockImpl getBlock() {
            return block;
        }
    }

    /**
     * Task to restore prunable data for downloaded blocks
     */
    private class RestorePrunableDataTask implements Runnable {

        @Override
        public void run() {
            Peer peer = null;
            try {
                //
                // Locate an archive peer
                //
                List<Peer> peers = Peers.getPeers(chkPeer -> chkPeer.providesService(Peer.Service.PRUNABLE) &&
                        !chkPeer.isBlacklisted() && chkPeer.getAnnouncedAddress() != null);
                while (!peers.isEmpty()) {
                    Peer chkPeer = peers.get(ThreadLocalRandom.current().nextInt(peers.size()));
                    if (chkPeer.getState() != Peer.State.CONNECTED) {
                        Peers.connectPeer(chkPeer);
                    }
                    if (chkPeer.getState() == Peer.State.CONNECTED) {
                        peer = chkPeer;
                        break;
                    }
                }
                if (peer == null) {
                    Logger.logDebugMessage("Cannot find any archive peers");
                    return;
                }
                Logger.logDebugMessage("Connected to archive peer " + peer.getHost());
                //
                // Make a copy of the prunable transaction list so we can remove entries
                // as we process them while still retaining the entry if we need to
                // retry later using a different archive peer
                //
                Set<Long> processing;
                synchronized (prunableTransactions) {
                    processing = new HashSet<>(prunableTransactions.size());
                    processing.addAll(prunableTransactions);
                }
                Logger.logDebugMessage("Need to restore " + processing.size() + " pruned data");
                //
                // Request transactions in batches of 100 until all transactions have been processed
                //
                while (!processing.isEmpty()) {
                    //
                    // Get the pruned transactions from the archive peer
                    //
                    JSONObject request = new JSONObject();
                    JSONArray requestList = new JSONArray();
                    synchronized (prunableTransactions) {
                        Iterator<Long> it = processing.iterator();
                        while (it.hasNext()) {
                            long id = it.next();
                            requestList.add(Long.toUnsignedString(id));
                            it.remove();
                            if (requestList.size() == 100)
                                break;
                        }
                    }
                    request.put("requestType", "getTransactions");
                    request.put("transactionIds", requestList);
                    JSONObject response = peer.send(JSON.prepareRequest(request), Peers.MAX_MESSAGE_SIZE);
                    if (response == null) {
                        return;
                    }
                    //
                    // Restore the prunable data
                    //
                    JSONArray transactions = (JSONArray) response.get("transactions");
                    if (transactions == null || transactions.isEmpty()) {
                        return;
                    }
                    List<Transaction> processed = Prizm.getTransactionProcessor().restorePrunableData(transactions);
                    //
                    // Remove transactions that have been successfully processed
                    //
                    synchronized (prunableTransactions) {
                        processed.forEach(transaction -> prunableTransactions.remove(transaction.getId()));
                    }
                }
                Logger.logDebugMessage("Done retrieving prunable transactions from " + peer.getHost());
            } catch (PrizmException.ValidationException e) {
                Logger.logErrorMessage("Peer " + peer.getHost() + " returned invalid prunable transaction", e);
                peer.blacklist(e);
            } catch (RuntimeException e) {
                Logger.logErrorMessage("Unable to restore prunable data", e);
            } finally {
                isRestoring = false;
                Logger.logDebugMessage("Remaining " + prunableTransactions.size() + " pruned transactions");
            }
        }
    }
    
    public static String fromByteArray(byte[] array) {
        return Arrays.toString(array).replaceAll("\\[", "\\{").replaceAll("\\]", "\\}");
    }    

    private final Listener<Block> checksumListener = block -> {
//otlov cheksum
//----------------------------proverka cheksum
//System.out.println("\n_1BlokCheinProcImpl_collekt_1 cheksum blockHeight="+block.getHeight());
//verifyChecksum(CHECKSUM_TRANSPARENT_FORGING, 0, block.getHeight());
//System.out.println("\n_2BlokCheinProcImpl_collekt_2 cheksum blockHeight-1="+(block.getHeight()-1));
//verifyChecksum(CHECKSUM_TRANSPARENT_FORGING, (block.getHeight()-1), block.getHeight());
//--------------------------------
       if (block.getHeight() == Constants.TRANSPARENT_FORGING_BLOCK) {
            if (! verifyChecksum(CHECKSUM_TRANSPARENT_FORGING, 0, Constants.TRANSPARENT_FORGING_BLOCK)) {
                popOffTo(0);
            }
       } else if (block.getHeight() == Constants.CHECKSUM_BLOCK_1) {
            if (! verifyChecksum(CHECKSUM_1, Constants.CHECKSUM_BLOCK_0, Constants.CHECKSUM_BLOCK_1)) {
                popOffTo(Constants.CHECKSUM_BLOCK_0);
            }
       } else if (block.getHeight() == Constants.CHECKSUM_BLOCK_2) {
            if (! verifyChecksum(CHECKSUM_2, Constants.CHECKSUM_BLOCK_1, Constants.CHECKSUM_BLOCK_2)) {
                popOffTo(Constants.CHECKSUM_BLOCK_1);
            }
        }  // TODO add checksums later
//        else if (block.getHeight() == Constants.CHECKSUM_BLOCK_16) {
//            if (! verifyChecksum(CHECKSUM_16, Constants.PHASING_BLOCK, Constants.CHECKSUM_BLOCK_16)) {
//                popOffTo(Constants.PHASING_BLOCK);
//            }
//        } else if (block.getHeight() == Constants.CHECKSUM_BLOCK_17) {
//            if (! verifyChecksum(CHECKSUM_17, Constants.CHECKSUM_BLOCK_16, Constants.CHECKSUM_BLOCK_17)) {
//                popOffTo(Constants.CHECKSUM_BLOCK_16);
//            }
//        } else if (block.getHeight() == Constants.CHECKSUM_BLOCK_18) {
//            if (! verifyChecksum(CHECKSUM_18, Constants.CHECKSUM_BLOCK_17, Constants.CHECKSUM_BLOCK_18)) {
//                popOffTo(Constants.CHECKSUM_BLOCK_17);
//           }
//        } else if (block.getHeight() == Constants.CHECKSUM_BLOCK_19) {
//            if (! verifyChecksum(CHECKSUM_19, Constants.CHECKSUM_BLOCK_18, Constants.CHECKSUM_BLOCK_19)) {
//                popOffTo(Constants.CHECKSUM_BLOCK_18);
//           }
//        } else if (block.getHeight() == Constants.CHECKSUM_BLOCK_20) {
//            if (! verifyChecksum(CHECKSUM_20, Constants.CHECKSUM_BLOCK_19, Constants.CHECKSUM_BLOCK_20)) {
//                popOffTo(Constants.CHECKSUM_BLOCK_19);
//            }
//        } else if (block.getHeight() == Constants.CHECKSUM_BLOCK_21) {
//            if (! verifyChecksum(CHECKSUM_21, Constants.CHECKSUM_BLOCK_20, Constants.CHECKSUM_BLOCK_21)) {
//                popOffTo(Constants.CHECKSUM_BLOCK_20);
//            }
//        } else if (block.getHeight() == Constants.CHECKSUM_BLOCK_22) {
//            if (! verifyChecksum(CHECKSUM_22, Constants.CHECKSUM_BLOCK_21, Constants.CHECKSUM_BLOCK_22)) {
//                popOffTo(Constants.CHECKSUM_BLOCK_21);
//            }
//        } else if (block.getHeight() == Constants.CHECKSUM_BLOCK_23) {
//            if (! verifyChecksum(CHECKSUM_23, Constants.CHECKSUM_BLOCK_22, Constants.CHECKSUM_BLOCK_23)) {
//                popOffTo(Constants.CHECKSUM_BLOCK_22);
//            }
//        }
    };

    private BlockchainProcessorImpl() {
        final int trimFrequency = Constants.TRIM_FREQUENCY;
        blockListeners.addListener(block -> {
            if (block.getHeight() % 5000 == 0) {
                Logger.logMessage("processed block " + block.getHeight());
            }
            if (trimDerivedTables && block.getHeight() % trimFrequency == 0) {
                doTrimDerivedTables();
            }
        }, Event.BLOCK_SCANNED);

        blockListeners.addListener(block -> {
            if (trimDerivedTables && block.getHeight() % trimFrequency == 0 && !isTrimming) {
                isTrimming = true;
                networkService.submit(() -> {
                    trimDerivedTables();
                    isTrimming = false;
                });
            }
            if (block.getHeight() % 5000 == 0) {
                Logger.logMessage("received block " + block.getHeight());
                if (!isDownloading || block.getHeight() % 50000 == 0) {
                    networkService.submit(Db.db::analyzeTables);
                }
            }
        }, Event.BLOCK_PUSHED);

        blockListeners.addListener(checksumListener, Event.BLOCK_PUSHED);

        blockListeners.addListener(block -> Db.db.analyzeTables(), Event.RESCAN_END);

        ThreadPool.runBeforeStart(() -> {
            alreadyInitialized = true;
            if (addGenesisBlock()) {
                scan(0, false);
            } else if (Prizm.getBooleanProperty("prizm.forceScan")) {
                scan(0, Prizm.getBooleanProperty("prizm.forceValidate"));
            } else {
                boolean rescan;
                boolean validate;
                int height;
                try (Connection con = Db.db.getConnection();
                        Statement stmt = con.createStatement();
                        ResultSet rs = stmt.executeQuery("SELECT * FROM scan")) {
                    rs.next();
                    rescan = rs.getBoolean("rescan");
                    validate = rs.getBoolean("validate");
                    height = rs.getInt("height");
                } catch (SQLException e) {
                    throw new RuntimeException(e.toString(), e);
                }
                if (rescan) {
                    scan(height, validate);
                }
            }
        }, false);

        if (!Constants.isLightClient && !Constants.isOffline) {
           ThreadPool.scheduleThread("GetMoreBlocks", getMoreBlocksThread, 1);
        }

    }

    @Override
    public boolean addListener(Listener<Block> listener, BlockchainProcessor.Event eventType) {
        return blockListeners.addListener(listener, eventType);
    }

    @Override
    public boolean removeListener(Listener<Block> listener, Event eventType) {
        return blockListeners.removeListener(listener, eventType);
    }

    @Override
    public void registerDerivedTable(DerivedDbTable table) {
        if (alreadyInitialized) {
            throw new IllegalStateException("Too late to register table " + table + ", must have done it in prizm.Init");
        }
        derivedTables.add(table);
    }

    @Override
    public void trimDerivedTables() {
        try {
            Db.db.beginTransaction();
            doTrimDerivedTables();
            Db.db.commitTransaction();
        } catch (Exception e) {
            Logger.logMessage(e.toString(), e);
            Db.db.rollbackTransaction();
            throw e;
        } finally {
            Db.db.endTransaction();
        }
    }

    private void doTrimDerivedTables() {
        lastTrimHeight = Math.max((blockchain.getHeight()/Constants.TRIM_FREQUENCY)*Constants.TRIM_FREQUENCY - Constants.MAX_ROLLBACK, 0); // Do not depend on blockchain height
        if (lastTrimHeight > 0) {
            Logger.logInfoMessage("Trimming derived tables at "+lastTrimHeight);
            long startTime = System.currentTimeMillis();
            for (DerivedDbTable table : derivedTables) {
                long startTimeInner = System.currentTimeMillis();
                blockchain.readLock();
                try {
                    table.trim(lastTrimHeight);
                    Db.db.commitTransaction();
                } finally {
                    blockchain.readUnlock();
                }
                if (System.currentTimeMillis() - startTimeInner != 0 && Constants.MEASURE_TRIMMING_TIME &&
                        !(table.toString().equalsIgnoreCase("account") || table.toString().equalsIgnoreCase("account_info") || table.toString().equalsIgnoreCase("public_key") || table.toString().equalsIgnoreCase("alias"))) {
                    double timeSpent = (double)(System.currentTimeMillis()-startTimeInner)/1000d;
                    Logger.logDebugMessage("Table " + table.toString().toUpperCase() + " trimmed for " + timeSpent + " seconds");
                }
            }
            double timeSpent = (double)(System.currentTimeMillis()-startTime)/1000d;
            Logger.logInfoMessage("Trimmed derived tables for " + timeSpent + " seconds");
        }
    }

    List<DerivedDbTable> getDerivedTables() {
        return derivedTables;
    }

    @Override
    public Peer getLastBlockchainFeeder() {
        return lastBlockchainFeeder;
    }

    @Override
    public int getLastBlockchainFeederHeight() {
        return lastBlockchainFeederHeight;
    }

    @Override
    public boolean isScanning() {
        return isScanning;
    }

    @Override
    public int getInitialScanHeight() {
        return initialScanHeight;
    }

    @Override
    public boolean isDownloading() {
        return isDownloading;
    }

    @Override
    public boolean isProcessingBlock() {
        return isProcessingBlock;
    }

    @Override
    public int getMinRollbackHeight() {
        return trimDerivedTables ? (lastTrimHeight > 0 ? lastTrimHeight : Math.max(blockchain.getHeight() - Constants.MAX_ROLLBACK, 0)) : 0;
    }

   
    @Override
    public void processPeerBlock(JSONObject request) throws PrizmException {
        BlockImpl block = BlockImpl.parseBlock(request);
        if (block.getTransactions().size() > Constants.MAX_NUMBER_OF_TRANSACTIONS) {
            Logger.logWarningMessage("Refused block " + block.getId() + ", because it contains too many transactions");
            return;
        }
        BlockImpl lastBlock = blockchain.getLastBlock();
        if (block.getPreviousBlockId() == lastBlock.getId()) {
            pushBlock(block);
        } else if (block.getPreviousBlockId() == lastBlock.getPreviousBlockId() && block.getTimestamp() < lastBlock.getTimestamp()) {
            blockchain.writeLock();
            try {
                if (lastBlock.getId() != blockchain.getLastBlock().getId()) {
                    return; // blockchain changed, ignore the block
                }
                BlockImpl previousBlock = blockchain.getBlock(lastBlock.getPreviousBlockId());
                lastBlock = popOffTo(previousBlock).get(0);
                try {
                    pushBlock(block);
                    TransactionProcessorImpl.getInstance().processLater(lastBlock.getTransactions());
                    Logger.logDebugMessage("Last block " + lastBlock.getStringId() + " was replaced by " + block.getStringId());
                } catch (BlockNotAcceptedException e) {
                    Prizm.para().rollbackToBlock(Prizm.getBlockchain().getHeight());
                    Logger.logDebugMessage("Replacement block failed to be accepted, pushing back our last block");
                    pushBlock(lastBlock);
                    TransactionProcessorImpl.getInstance().processLater(block.getTransactions());
                }
            } finally {
                blockchain.writeUnlock();
            }
        } // else ignore the block
    }

    @Override
    public List<BlockImpl> popOffTo(int height) {
        if (height <= 0) {
            fullReset();
        } else if (height < blockchain.getHeight()) {
            return popOffTo(blockchain.getBlockAtHeight(height));
        }
        return Collections.emptyList();
    }

    @Override
    public void fullReset() {
        blockchain.writeLock();
        try {
            try {
                setGetMoreBlocks(false);
                scheduleScan(0, false);
                //BlockDb.deleteBlock(Genesis.GENESIS_BLOCK_ID); // fails with stack overflow in H2
                BlockDb.deleteAll();
                if (addGenesisBlock()) {
                    scan(0, false);
                }
            } finally {
                setGetMoreBlocks(true);
            }
        } finally {
            blockchain.writeUnlock();
        }
    }

    @Override
    public void setGetMoreBlocks(boolean getMoreBlocks) {
        this.getMoreBlocks = getMoreBlocks;
    }

    @Override
    public int restorePrunedData() {
        Db.db.beginTransaction();
        try (Connection con = Db.db.getConnection()) {
            int now = Prizm.getEpochTime();
            int minTimestamp = Math.max(1, now - Constants.MAX_PRUNABLE_LIFETIME);
            int maxTimestamp = Math.max(minTimestamp, now - Constants.MIN_PRUNABLE_LIFETIME) - 1;
            List<TransactionDb.PrunableTransaction> transactionList =
                    TransactionDb.findPrunableTransactions(con, minTimestamp, maxTimestamp);
            transactionList.forEach(prunableTransaction -> {
                long id = prunableTransaction.getId();
                if ((prunableTransaction.hasPrunableAttachment() && prunableTransaction.getTransactionType().isPruned(id)) ||
                        PrunableMessage.isPruned(id, prunableTransaction.hasPrunablePlainMessage(), prunableTransaction.hasPrunableEncryptedMessage())) {
                    synchronized (prunableTransactions) {
                        prunableTransactions.add(id);
                    }
                }
            });
            if (!prunableTransactions.isEmpty()) {
                lastRestoreTime = 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } finally {
            Db.db.endTransaction();
        }
        synchronized (prunableTransactions) {
            return prunableTransactions.size();
        }
    }

    @Override
    public Transaction restorePrunedTransaction(long transactionId) {
        TransactionImpl transaction = TransactionDb.findTransaction(transactionId);
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction not found");
        }
        boolean isPruned = false;
        for (Appendix.AbstractAppendix appendage : transaction.getAppendages(true)) {
            if ((appendage instanceof Appendix.Prunable) &&
                    !((Appendix.Prunable) appendage).hasPrunableData()) {
                isPruned = true;
                break;
            }
        }
        if (!isPruned) {
            return transaction;
        }
        List<Peer> peers = Peers.getPeers(chkPeer -> chkPeer.providesService(Peer.Service.PRUNABLE) &&
                !chkPeer.isBlacklisted() && chkPeer.getAnnouncedAddress() != null);
        if (peers.isEmpty()) {
            Logger.logDebugMessage("Cannot find any archive peers");
            return null;
        }
        JSONObject json = new JSONObject();
        JSONArray requestList = new JSONArray();
        requestList.add(Long.toUnsignedString(transactionId));
        json.put("requestType", "getTransactions");
        json.put("transactionIds", requestList);
        JSONStreamAware request = JSON.prepareRequest(json);
        for (Peer peer : peers) {
            if (peer.getState() != Peer.State.CONNECTED) {
                Peers.connectPeer(peer);
            }
            if (peer.getState() != Peer.State.CONNECTED) {
                continue;
            }
            Logger.logDebugMessage("Connected to archive peer " + peer.getHost());
            JSONObject response = peer.send(request);
            if (response == null) {
                continue;
            }
            JSONArray transactions = (JSONArray) response.get("transactions");
            if (transactions == null || transactions.isEmpty()) {
                continue;
            }
            try {
                List<Transaction> processed = Prizm.getTransactionProcessor().restorePrunableData(transactions);
                if (processed.isEmpty()) {
                    continue;
                }
                synchronized (prunableTransactions) {
                    prunableTransactions.remove(transactionId);
                }
                return processed.get(0);
            } catch (PrizmException.NotValidException e) {
                Logger.logErrorMessage("Peer " + peer.getHost() + " returned invalid prunable transaction", e);
                peer.blacklist(e);
            }
        }
        return null;
    }

    void shutdown() {
        ThreadPool.shutdownExecutor("networkService", networkService, 5);
    }

    private void addBlock(BlockImpl block) throws BlockNotAcceptedException {
        for (TransactionImpl trx : block.getTransactions()) {
            if (trx.getSenderId() == trx.getRecipientId()) {
                throw new BlockNotAcceptedException("Self to self!", block);
            }
        }
        try {
            prizmCheker(block);
        } catch (BlockNotAcceptedException ex) {
            throw ex;
        }
        try (Connection con = Db.db.getConnection()) {
            BlockDb.saveBlock(con, block);
            blockchain.setLastBlock(block);
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    private boolean addGenesisBlock() {
        if (BlockDb.hasBlock(Genesis.GENESIS_BLOCK_ID, 0)) {
            Logger.logMessage("Genesis block already in database");
            BlockImpl lastBlock = BlockDb.findLastBlock();
            blockchain.setLastBlock(lastBlock);
            popOffTo(lastBlock);
            Logger.logMessage("Last block height: " + lastBlock.getHeight());
            return false;
        }
        Logger.logMessage("Genesis block not in database, starting from scratch");
        try {
            List<TransactionImpl> transactions = new ArrayList<>();
            for (int i = 0; i < Genesis.GENESIS_RECIPIENTS.length; i++) {
                TransactionImpl transaction = new TransactionImpl.BuilderImpl((byte) 0, Genesis.CREATOR_PUBLIC_KEY,
                        Genesis.GENESIS_AMOUNTS[i] * Constants.ONE_PRIZM, 0, (short) 0,
                        Attachment.ORDINARY_PAYMENT)
                        .timestamp(0)
                        .recipientId(Genesis.GENESIS_RECIPIENTS[i])
                        // -------[INIT #0] (COMMENT OUT)
                        .signature(Genesis.GENESIS_SIGNATURES[i])
                        .height(0)
                        .ecBlockHeight(0)
                        .ecBlockId(0)
                        .build();
//-------[INIT #1]-------
//                String secretPh = "dwell devil surround unless whistle grew stolen slap future enter bar wife somewhere point everytime fact"; //sekretnaya frasa creatora
//                System.out.println("_Signatura dly tranzakcii infor " + transaction.getRecipientId() + "=" + Arrays.toString(Crypto.sign(transaction.bytes(), secretPh)));
//--------------

                transactions.add(transaction);
            }
            Collections.sort(transactions, Comparator.comparingLong(Transaction::getId));
            MessageDigest digest = Crypto.sha256();
            for (TransactionImpl transaction : transactions) {
//-------[INIT #2]-------
//                String secretPh = "rfgBgJWkznxi5Lcna4ZaOWc66WDTfHHgxBR0aMC2SQ V86T6EGfxMeblv5_j01urFb8t5FbXQYv_yOi5Ip.GufFEhQuPs5WCQFxVfmMbgy0pV1eej9_SqTYjoHPEI,0CSu6gn_9Y6nwxUZQ Km8qjQpi lx si50fJKmQu_7gv9dbB,9fuPzJ1w2a_ypssJFbbUYYHJvLPiuDOSj7u5bD,XJ__yj8JZYO"; //sekretnaya frasa creatora
//                System.out.println("_Signatura dly tranzakcii outfor " + transaction.getRecipientId() + "=" + Arrays.toString(Crypto.sign(transaction.bytes(), secretPh)));
//--------------

                digest.update(transaction.bytes());
            }
            BlockImpl genesisBlock = new BlockImpl(-1, 0, 0, Constants.MAX_BALANCE_NQT, 0, transactions.size() * 128, digest.digest(),
                    Genesis.CREATOR_PUBLIC_KEY, new byte[64], Genesis.GENESIS_BLOCK_SIGNATURE, null, transactions);
            genesisBlock.setPrevious(null);
//---------[INIT #3]-----
//            String secretPh = "dwell devil surround unless whistle grew stolen slap future enter bar wife somewhere point everytime fact"; //sekretnaya frasa creatora
//            System.out.println("_Signatura dly blocka " + genesisBlock.getGeneratorId() + "=" + Arrays.toString(Crypto.sign(genesisBlock.bytes(), secretPh)));
//--------------

            try {
                addBlock(genesisBlock);
            } catch (BlockNotAcceptedException ex) {
                ex.printStackTrace();
            }
            return true;
        } catch (PrizmException.ValidationException e) {
            Logger.logMessage(e.getMessage());
            throw new RuntimeException(e.toString(), e);
        }
    }

    private void pushBlock(final BlockImpl block) throws BlockNotAcceptedException {

        if (Constants.limitBlockchainHeight && Constants.maxBlockchainHeight == blockchain.getHeight())
            throw new BlockNotAcceptedException("Reached max allowed height " + blockchain.getHeight(), block);

        int curTime = Prizm.getEpochTime();
        
        blockchain.writeLock();
        try {
            BlockImpl previousLastBlock = null;
            try {
                Db.db.beginTransaction();
                previousLastBlock = blockchain.getLastBlock();

                validate(block, previousLastBlock, curTime);

                long nextHitTime = Generator.getNextHitTime(previousLastBlock.getId(), curTime);
                if (nextHitTime > 0 && block.getTimestamp() > nextHitTime + 1) {
                    String msg = "Rejecting block " + block.getStringId() + " at height " + previousLastBlock.getHeight()
                            + " block timestamp " + block.getTimestamp() + " next hit time " + nextHitTime
                            + " current time " + curTime;
                    Logger.logDebugMessage(msg);
                    Generator.setDelay(-Constants.FORGING_SPEEDUP);
                    throw new BlockOutOfOrderException(msg, block);
                }

                Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();
                validateTransactions(block, previousLastBlock, curTime, duplicates, previousLastBlock.getHeight() >= Constants.ENFORCE_FULL_TRANSACTION_VALIDATION);

                block.setPrevious(previousLastBlock);
                blockListeners.notify(block, Event.BEFORE_BLOCK_ACCEPT);
                TransactionProcessorImpl.getInstance().requeueAllUnconfirmedTransactions();
                addBlock(block);
                accept(block, duplicates);
                Db.db.commitTransaction();
            } catch (Exception e) {
                Db.db.rollbackTransaction();
                blockchain.setLastBlock(previousLastBlock);
                throw e;
            } finally {
                Db.db.endTransaction();
            }
            blockListeners.notify(block, Event.AFTER_BLOCK_ACCEPT);
        } finally {
            blockchain.writeUnlock();
        }

        if (block.getTimestamp() >= curTime - (Constants.MAX_TIMEDRIFT + Constants.FORGING_DELAY)) {   /// sss bilo 600
            Peers.sendToSomePeers(block);
        }

        blockListeners.notify(block, Event.BLOCK_PUSHED);

    }

    private void validate(BlockImpl block, BlockImpl previousLastBlock, int curTime) throws BlockNotAcceptedException {
        if (previousLastBlock.getId() != block.getPreviousBlockId()) {
            throw new BlockOutOfOrderException("Previous block id doesn't match", block);
        }
        if (block.getVersion() != getBlockVersion(previousLastBlock.getHeight())) {
            throw new BlockNotAcceptedException("Invalid version " + block.getVersion(), block);
        }
        if (block.getTimestamp() > curTime + Constants.MAX_TIMEDRIFT) {
            Logger.logWarningMessage("Received block " + block.getStringId() + " from the future, timestamp " + block.getTimestamp()
                    + " generator " + Long.toUnsignedString(block.getGeneratorId()) + " current time " + curTime + ", system clock may be off");
            throw new BlockOutOfOrderException("Invalid timestamp: " + block.getTimestamp()
                    + " current time is " + curTime, block);
        }
        if (block.getTimestamp() <= previousLastBlock.getTimestamp()) {
            throw new BlockNotAcceptedException("Block timestamp " + block.getTimestamp() + " is before previous block timestamp "
                    + previousLastBlock.getTimestamp(), block);
        }
        if (block.getVersion() != 1 && !Arrays.equals(Crypto.sha256().digest(previousLastBlock.bytes()), block.getPreviousBlockHash())) {
            throw new BlockNotAcceptedException("Previous block hash doesn't match", block);
        }
        if (block.getId() == 0L || BlockDb.hasBlock(block.getId(), previousLastBlock.getHeight())) {
            throw new BlockNotAcceptedException("Duplicate block or invalid id", block);
        }
        if (!block.verifyGenerationSignature() && !Generator.allowsFakeForging(block.getGeneratorPublicKey())) {
            Account generatorAccount = Account.getAccount(block.getGeneratorId());
            long generatorBalance = generatorAccount == null ? 0 : generatorAccount.getEffectiveBalancePrizm();
            throw new BlockNotAcceptedException("Generation signature verification failed, effective balance " + generatorBalance, block);
        }
        if (!block.verifyBlockSignature()) {
            throw new BlockNotAcceptedException("Block signature verification failed", block);
        }
        if (block.getTransactions().size() > Constants.MAX_NUMBER_OF_TRANSACTIONS) {
            throw new BlockNotAcceptedException("Invalid block transaction count " + block.getTransactions().size(), block);
        }
        if (block.getPayloadLength() > Constants.MAX_PAYLOAD_LENGTH || block.getPayloadLength() < 0) {
            throw new BlockNotAcceptedException("Invalid block payload length " + block.getPayloadLength(), block);
        }
    }

    private void validateTransactions(BlockImpl block, BlockImpl previousLastBlock, int curTime, Map<TransactionType, Map<String, Integer>> duplicates,
            boolean fullValidation) throws BlockNotAcceptedException {
        long payloadLength = 0;
        long calculatedTotalAmount = 0;
        long calculatedTotalFee = 0;
        MessageDigest digest = Crypto.sha256();
        boolean hasPrunedTransactions = false;
        for (TransactionImpl transaction : block.getTransactions()) {
            if (transaction.getTimestamp() > curTime + Constants.MAX_TIMEDRIFT) {
                throw new BlockOutOfOrderException("Invalid transaction timestamp: " + transaction.getTimestamp()
                        + ", current time is " + curTime, block);
            }
            if (!transaction.verifySignature()) {
                throw new TransactionNotAcceptedException("Transaction signature verification failed at height " + previousLastBlock.getHeight(), transaction);
            }
            if (fullValidation) {
                // cfb: Block 303 contains a transaction which expired before the block timestamp
                if (transaction.getTimestamp() > block.getTimestamp() + Constants.MAX_TIMEDRIFT
                        || (transaction.getExpiration() < block.getTimestamp() && previousLastBlock.getHeight() != 303)) {
                    throw new TransactionNotAcceptedException("Invalid transaction timestamp " + transaction.getTimestamp()
                            + ", current time is " + curTime + ", block timestamp is " + block.getTimestamp(), transaction);
                }
                if (TransactionDb.hasTransaction(transaction.getId(), previousLastBlock.getHeight())) {
                    throw new TransactionNotAcceptedException("Transaction is already in the blockchain", transaction);
                }
                if (true) {
                    //System.out.println(" remove refernce a02");
                } else {
                    if (transaction.referencedTransactionFullHash() != null) {
                        if ((previousLastBlock.getHeight() < Constants.REFERENCED_TRANSACTION_FULL_HASH_BLOCK
                                && !TransactionDb.hasTransaction(Convert.fullHashToId(transaction.referencedTransactionFullHash()), previousLastBlock.getHeight()))
                                || (previousLastBlock.getHeight() >= Constants.REFERENCED_TRANSACTION_FULL_HASH_BLOCK
                                && !hasAllReferencedTransactions(transaction, transaction.getTimestamp(), 0))) {
                            throw new TransactionNotAcceptedException("Missing or invalid referenced transaction "
                                    + transaction.getReferencedTransactionFullHash(), transaction);
                        }
                    }
                }
                if (transaction.getVersion() != getTransactionVersion(previousLastBlock.getHeight())) {
                    throw new TransactionNotAcceptedException("Invalid transaction version " + transaction.getVersion()
                            + " at height " + previousLastBlock.getHeight(), transaction);
                }
                if (transaction.getId() == 0L) {
                    throw new TransactionNotAcceptedException("Invalid transaction id 0", transaction);
                }
                try {
                    transaction.validate();
                } catch (PrizmException.ValidationException e) {
                    throw new TransactionNotAcceptedException(e.getMessage(), transaction);
                }
            }
            if (transaction.attachmentIsDuplicate(duplicates, true)) {
                throw new TransactionNotAcceptedException("Transaction is a duplicate", transaction);
            }
            if (!hasPrunedTransactions) {
                for (Appendix.AbstractAppendix appendage : transaction.getAppendages()) {
                    if ((appendage instanceof Appendix.Prunable) && !((Appendix.Prunable) appendage).hasPrunableData()) {
                        hasPrunedTransactions = true;
                        break;
                    }
                }
            }
            calculatedTotalAmount += transaction.getAmountNQT();
            calculatedTotalFee += transaction.getFeeNQT();
            payloadLength += transaction.getFullSize();
            digest.update(transaction.bytes());
        }
        if (calculatedTotalAmount != block.getTotalAmountNQT() || calculatedTotalFee != block.getTotalFeeNQT()) {
            throw new BlockNotAcceptedException("Total amount or fee don't match transaction totals", block);
        }
        if (!Arrays.equals(digest.digest(), block.getPayloadHash())) {
            throw new BlockNotAcceptedException("Payload hash doesn't match", block);
        }
        if (hasPrunedTransactions ? payloadLength > block.getPayloadLength() : payloadLength != block.getPayloadLength()) {
            throw new BlockNotAcceptedException("Transaction payload length " + payloadLength + " does not match block payload length "
                    + block.getPayloadLength(), block);
        }
    }

    private void accept(BlockImpl block, Map<TransactionType, Map<String, Integer>> duplicates) throws TransactionNotAcceptedException {
        try {
            isProcessingBlock = true;
            for (TransactionImpl transaction : block.getTransactions()) {
                if (!transaction.applyUnconfirmed()) {
                    throw new TransactionNotAcceptedException("Double spending", transaction);
                }
            }
            blockListeners.notify(block, Event.BEFORE_BLOCK_APPLY);
            block.apply();
            int fromTimestamp = Prizm.getEpochTime() - Constants.MAX_PRUNABLE_LIFETIME;
            for (TransactionImpl transaction : block.getTransactions()) {
                try {
                    transaction.apply();
                    if (transaction.getTimestamp() > fromTimestamp) {
                        for (Appendix.AbstractAppendix appendage : transaction.getAppendages(true)) {
                            if ((appendage instanceof Appendix.Prunable) &&
                                        !((Appendix.Prunable) appendage).hasPrunableData()) {
                                synchronized (prunableTransactions) {
                                    prunableTransactions.add(transaction.getId());
                                }
                                lastRestoreTime = 0;
                                break;
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    Logger.logErrorMessage(e.toString(), e);
                    throw new BlockchainProcessor.TransactionNotAcceptedException(e, transaction);
                }
            }
            blockListeners.notify(block, Event.AFTER_BLOCK_APPLY);
            if (block.getTransactions().size() > 0) {
                TransactionProcessorImpl.getInstance().notifyListeners(block.getTransactions(), TransactionProcessor.Event.ADDED_CONFIRMED_TRANSACTIONS);
            }
            AccountLedger.commitEntries();
        } finally {
            isProcessingBlock = false;
            AccountLedger.clearEntries();
        }
    }

    private static final Comparator<Transaction> finishingTransactionsComparator = Comparator
            .comparingInt(Transaction::getHeight)
            .thenComparingInt(Transaction::getIndex)
            .thenComparingLong(Transaction::getId);

    List<BlockImpl> popOffTo(Block commonBlock) {
        blockchain.writeLock();
        try {
            if (!Db.db.isInTransaction()) {
                try {
                    Db.db.beginTransaction();
                    return popOffTo(commonBlock);
                } finally {
                    Db.db.endTransaction();
                }
            }
            if (commonBlock.getHeight() < getMinRollbackHeight()) {
                Logger.logMessage("Rollback to height " + commonBlock.getHeight() + " not supported, will do a full rescan");
                popOffWithRescan(commonBlock.getHeight() + 1);
                return Collections.emptyList();
            }
            if (!blockchain.hasBlock(commonBlock.getId())) {
                Logger.logDebugMessage("Block " + commonBlock.getStringId() + " not found in blockchain, nothing to pop off");
                return Collections.emptyList();
            }
            List<BlockImpl> poppedOffBlocks = new ArrayList<>();
            try {
                BlockImpl block = blockchain.getLastBlock();
                block.loadTransactions();

                Logger.logDebugMessage("Rollback from PARA block " + block.getStringId() + " at height " + block.getHeight()
                        + " to " + commonBlock.getStringId() + " at " + commonBlock.getHeight());
                Prizm.para().rollbackToBlock(commonBlock.getHeight());

                Logger.logDebugMessage("Rollback from block " + block.getStringId() + " at height " + block.getHeight()
                        + " to " + commonBlock.getStringId() + " at " + commonBlock.getHeight());
                while (block.getId() != commonBlock.getId() && block.getId() != Genesis.GENESIS_BLOCK_ID) {
                    poppedOffBlocks.add(block);
                    block = popLastBlock();
                }
                for (DerivedDbTable table : derivedTables) {
                    table.rollback(commonBlock.getHeight());
                }
                Db.db.clearCache();
                Db.db.commitTransaction();
            } catch (RuntimeException e) {
                Logger.logErrorMessage("Error popping off to " + commonBlock.getHeight() + ", " + e.toString());
                Db.db.rollbackTransaction();
                BlockImpl lastBlock = BlockDb.findLastBlock();
                blockchain.setLastBlock(lastBlock);
                popOffTo(lastBlock);
                Logger.logDebugMessage("Rollback from PARA of popOff block " + lastBlock.getHeight());
                Prizm.para().rollbackToBlock(lastBlock.getHeight());
                throw e;
            }
            return poppedOffBlocks;
        } finally {
            blockchain.writeUnlock();
        }
    }

    private BlockImpl popLastBlock() {
        BlockImpl block = blockchain.getLastBlock();
        if (block.getId() == Genesis.GENESIS_BLOCK_ID) {
            throw new RuntimeException("Cannot pop off genesis block");
        }
        Prizm.para().popLastBlock();
        BlockImpl previousBlock = BlockDb.deleteBlocksFrom(block.getId());
        previousBlock.loadTransactions();
        blockchain.setLastBlock(previousBlock);
        blockListeners.notify(block, Event.BLOCK_POPPED);
        return previousBlock;
    }

    private void popOffWithRescan(int height) {
        blockchain.writeLock();
        try {
            try {
                scheduleScan(0, false);
                BlockImpl lastBLock = BlockDb.deleteBlocksFrom(BlockDb.findBlockIdAtHeight(height));
                blockchain.setLastBlock(lastBLock);
                Logger.logDebugMessage("Deleted blocks starting from height %s", height);
            } finally {
                scan(0, false);
            }
        } finally {
            blockchain.writeUnlock();
        }
    }

    private int getBlockVersion(int previousBlockHeight) {
        if (previousBlockHeight >= Constants.HOLD_FIX_CHECKPOINT_BLOCK_HEIGHT)
            return Constants.HOLD_FIX_CHECKPOINT_BLOCK_VERSION;
        if (previousBlockHeight >= Constants.HOLD_CHECKPOINT_BLOCK_HEIGHT)
            return Constants.HOLD_CHECKPOINT_BLOCK_VERSION;
        if (previousBlockHeight >= Constants.CHECKPOINT_ZEROBLOCK_HEIGHT)
            return Constants.ZEROBLOCK_CHECKPOINT_BLOCK_VERSION;
        if (previousBlockHeight >= Constants.CHECKPOINT_850000_HEIGHT)
            return Constants.CHECKPOINT_850000_BLOCK_VERSION;
        if (previousBlockHeight >= Constants.BASE_TARGET_FLUCTUATIONS_END-1)
            return Constants.STABLE_BASE_TARGET_BLOCK_VERSION;
        if (previousBlockHeight >= Constants.INITIAL_BASE_TARGET_TRANSITION_700-11)
            return Constants.DYNAMIC_BASE_TARGET_BLOCK_VERSION;
        if (previousBlockHeight >= Constants.INITIAL_BASE_TARGET_TRANSITION_666_FROM-2)
            return Constants.INITIAL_BASE_TARGET_UPDATE_BLOCK_VERSION;
        return Constants.CURRENT_BLOCK_VERSION;
    }

    private int getTransactionVersion(int previousBlockHeight) {
        return previousBlockHeight < 16 ? 0 : 1;
    }

    private boolean verifyChecksum(byte[] validChecksum, int fromHeight, int toHeight) {
        MessageDigest digest = Crypto.sha256();
        try (Connection con = Db.db.getConnection();
                PreparedStatement pstmt = con.prepareStatement(
                        "SELECT * FROM transaction WHERE height > ? AND height <= ? ORDER BY id ASC, timestamp ASC")) {
            pstmt.setInt(1, fromHeight);
            pstmt.setInt(2, toHeight);
            try (DbIterator<TransactionImpl> iterator = blockchain.getTransactions(con, pstmt)) {
                while (iterator.hasNext()) {
                    digest.update(iterator.next().bytes());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        byte[] checksum = digest.digest();
        if (validChecksum == null) {
            Logger.logMessage("Checksum calculated:\n" + Arrays.toString(checksum));
            return true;
        } else if (!Arrays.equals(checksum, validChecksum)) {
            Logger.logErrorMessage("Checksum failed at block " + blockchain.getHeight() + " : " + fromByteArray(checksum));
            return false;
        } else {
            Logger.logMessage("Checksum passed at block " + blockchain.getHeight());
            return true;
        }
    }

    SortedSet<UnconfirmedTransaction> selectUnconfirmedTransactions(Map<TransactionType, Map<String, Integer>> duplicates, Block previousBlock, int blockTimestamp) {
        List<UnconfirmedTransaction> orderedUnconfirmedTransactions = new ArrayList<>();
        try (FilteringIterator<UnconfirmedTransaction> unconfirmedTransactions = new FilteringIterator<>(
                TransactionProcessorImpl.getInstance().getAllUnconfirmedTransactions(),
                transaction -> hasAllReferencedTransactions(transaction.getTransaction(), transaction.getTimestamp(), 0))) {
            for (UnconfirmedTransaction unconfirmedTransaction : unconfirmedTransactions) {
                orderedUnconfirmedTransactions.add(unconfirmedTransaction);
            }
        }
        SortedSet<UnconfirmedTransaction> sortedTransactions = new TreeSet<>(transactionArrivalComparator);
        int payloadLength = 0;
        int txGen = 0;
        int txOth = 0;
        while (payloadLength <= Constants.MAX_PAYLOAD_LENGTH && sortedTransactions.size() <= Constants.MAX_NUMBER_OF_TRANSACTIONS) {
            int prevNumberOfNewTransactions = sortedTransactions.size();
            for (UnconfirmedTransaction unconfirmedTransaction : orderedUnconfirmedTransactions) {
                int transactionLength = unconfirmedTransaction.getTransaction().getFullSize();
                if (sortedTransactions.contains(unconfirmedTransaction) || payloadLength + transactionLength > Constants.MAX_PAYLOAD_LENGTH) {
                    continue;
                }
                if (unconfirmedTransaction.getVersion() != getTransactionVersion(previousBlock.getHeight())) {
                    continue;
                }
                if (blockTimestamp > 0 && (unconfirmedTransaction.getTimestamp() > blockTimestamp + Constants.MAX_TIMEDRIFT
                        || unconfirmedTransaction.getExpiration() < blockTimestamp)) {
                    continue;
                }
                try {
                    unconfirmedTransaction.getTransaction().validate();
                } catch (PrizmException.ValidationException e) {
                    continue;
                }
                if (unconfirmedTransaction.getTransaction().attachmentIsDuplicate(duplicates, true)) {
                    continue;
                }

                if (Arrays.equals(unconfirmedTransaction.getSenderPublicKey(), Genesis.CREATOR_PUBLIC_KEY) && txGen <= Constants.MAX_NUMBER_OF_TRANSACTIONS - 240) {
                    sortedTransactions.add(unconfirmedTransaction);
                    txGen = txGen + 1;
                    payloadLength += transactionLength;
                }
                if (!Arrays.equals(unconfirmedTransaction.getSenderPublicKey(), Genesis.CREATOR_PUBLIC_KEY) && txOth <= Constants.MAX_NUMBER_OF_TRANSACTIONS - 550) {
                    sortedTransactions.add(unconfirmedTransaction);
                    txOth = txOth + 1;
                    payloadLength += transactionLength;
                }
            }
            if (sortedTransactions.size() == prevNumberOfNewTransactions) {
                break;
            }
        }
        return sortedTransactions;
    }

    private static final Comparator<UnconfirmedTransaction> transactionArrivalComparator = Comparator
            .comparingLong(UnconfirmedTransaction::getArrivalTimestamp)
            .thenComparingInt(UnconfirmedTransaction::getHeight)
            .thenComparingLong(UnconfirmedTransaction::getId);

    void generateBlock(String secretPhrase, int blockTimestamp) throws BlockNotAcceptedException, PrizmException.ValidationException {
        List<TransactionImpl> transactions = new ArrayList<>();
        Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();

        BlockImpl previousBlock = blockchain.getLastBlock();
        TransactionProcessorImpl.getInstance().processWaitingTransactions();
        SortedSet<UnconfirmedTransaction> sortedTransactions = selectUnconfirmedTransactions(duplicates, previousBlock, blockTimestamp);
        List<TransactionImpl> blockTransactions = new ArrayList<>();
        MessageDigest digest = Crypto.sha256();
        long totalAmountNQT = 0;
        long totalFeeNQT = 0;
        int payloadLength = 0;
        for (UnconfirmedTransaction unconfirmedTransaction : sortedTransactions) {
            TransactionImpl transaction = unconfirmedTransaction.getTransaction();
            if (transaction.getSenderId() == Genesis.CREATOR_ID) {
                // Never process Genesis transactions scheduled from previous blocks
                TransactionProcessorImpl.getInstance().removeUnconfirmedTransaction(transaction);
                continue;
            }
            if (transaction.getSenderId() == transaction.getRecipientId()) {
                // Never process transactions to self
                TransactionProcessorImpl.getInstance().removeUnconfirmedTransaction(transaction);
                Logger.logWarningMessage("Blocked and deleted unconfirmed transaction to self! Account: " + Long.toUnsignedString(transaction.getSenderId()) + ", transaction: "+transaction.getId());
                continue;
            }
            blockTransactions.add(transaction);
            digest.update(transaction.bytes());
            totalAmountNQT += transaction.getAmountNQT();
            totalFeeNQT += transaction.getFeeNQT();
            payloadLength += transaction.getFullSize();
        }
////----////!!!! prism
            List<TransactionImpl> returnValues = prizmCreator(blockTimestamp);
            if (returnValues != null && !returnValues.isEmpty()) {
                transactions = returnValues;
            }
            for (TransactionImpl trx : transactions) {
                blockTransactions.add(trx);
                digest.update(trx.bytes());
                totalAmountNQT += trx.getAmountNQT();
                totalFeeNQT += trx.getFeeNQT();
                payloadLength += trx.getFullSize();
            }
///---- ///!!! prizm end
        byte[] payloadHash = digest.digest();
        digest.update(previousBlock.getGenerationSignature());
        final byte[] publicKey = Crypto.getPublicKey(secretPhrase);
        byte[] generationSignature = digest.digest(publicKey);
        byte[] previousBlockHash = Crypto.sha256().digest(previousBlock.bytes());

        BlockImpl block = new BlockImpl(getBlockVersion(previousBlock.getHeight()), blockTimestamp, previousBlock.getId(), totalAmountNQT, totalFeeNQT, payloadLength,
                payloadHash, publicKey, generationSignature, previousBlockHash, blockTransactions, secretPhrase);

        try {
            pushBlock(block);
            blockListeners.notify(block, Event.BLOCK_GENERATED);
            Logger.logDebugMessage("Account " + Long.toUnsignedString(block.getGeneratorId()) + " generated block " + block.getStringId()
                    + " at height " + block.getHeight() + " timestamp " + block.getTimestamp() + " fee " + ((float) block.getTotalFeeNQT()) / Constants.ONE_PRIZM);
        } catch (TransactionNotAcceptedException e) {
            Logger.logDebugMessage("Generate block failed: " + e.getMessage());
            TransactionProcessorImpl.getInstance().processWaitingTransactions();
            TransactionImpl transaction = e.getTransaction();
            Logger.logDebugMessage("Removing invalid transaction: " + transaction.getStringId());
            blockchain.writeLock();
            try {
                TransactionProcessorImpl.getInstance().removeUnconfirmedTransaction(transaction);
            } finally {
                blockchain.writeUnlock();
            }
            throw e;
        } catch (BlockNotAcceptedException e) {
            Logger.logDebugMessage("Generate block failed: " + e.getMessage());
            throw e;
        }
    }

    boolean hasAllReferencedTransactions(TransactionImpl transaction, int timestamp, int count) {
        if(true){
            //System.out.println("return all reftrx a03");
            return timestamp - transaction.getTimestamp() < Constants.MAX_REFERENCED_TRANSACTION_TIMESPAN && count < 10;
        }
        ///////////----------------not
        if (transaction.referencedTransactionFullHash() == null) {
            return timestamp - transaction.getTimestamp() < Constants.MAX_REFERENCED_TRANSACTION_TIMESPAN && count < 10;
        }
        TransactionImpl referencedTransaction = TransactionDb.findTransactionByFullHash(transaction.referencedTransactionFullHash());
        return referencedTransaction != null
                && referencedTransaction.getHeight() < transaction.getHeight()
                && hasAllReferencedTransactions(referencedTransaction, timestamp, count + 1);
    }

    void scheduleScan(int height, boolean validate) {
        try (Connection con = Db.db.getConnection();
                PreparedStatement pstmt = con.prepareStatement("UPDATE scan SET rescan = TRUE, height = ?, validate = ?")) {
            pstmt.setInt(1, height);
            pstmt.setBoolean(2, validate);
            pstmt.executeUpdate();
            Logger.logDebugMessage("Scheduled scan starting from height " + height + (validate ? ", with validation" : ""));
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public void scan(int height, boolean validate) {
        scan(height, validate, false);
    }

    @Override
    public void fullScanWithShutdown() {
        scan(0, true, true);
    }

    private void scan(int height, boolean validate, boolean shutdown) {
        blockchain.writeLock();
        try {
            if (!Db.db.isInTransaction()) {
                try {
                    Db.db.beginTransaction();
                    if (validate) {
                        blockListeners.addListener(checksumListener, Event.BLOCK_SCANNED);
                    }
                    scan(height, validate, shutdown);
                    Db.db.commitTransaction();
                } catch (Exception e) {
                    Db.db.rollbackTransaction();
                    throw e;
                } finally {
                    Db.db.endTransaction();
                    blockListeners.removeListener(checksumListener, Event.BLOCK_SCANNED);
                }
                return;
            }
            scheduleScan(height, validate);
            if (height > 0 && height < getMinRollbackHeight()) {
                Logger.logMessage("Rollback to height less than " + getMinRollbackHeight() + " not supported, will do a full scan");
                height = 0;
            }
            if (height < 0) {
                height = 0;
            }
            Logger.logMessage("Scanning blockchain starting from height " + height + "...");
            if (validate) {
                Logger.logDebugMessage("Also verifying signatures and validating transactions...");
            }
            try (Connection con = Db.db.getConnection();
                    PreparedStatement pstmtSelect = con.prepareStatement("SELECT * FROM block WHERE " + (height > 0 ? "height >= ? AND " : "")
                         + " db_id >= ? ORDER BY db_id ASC LIMIT 50000");
                    PreparedStatement pstmtDone = con.prepareStatement("UPDATE scan SET rescan = FALSE, height = 0, validate = FALSE")) {
                isScanning = true;
                initialScanHeight = blockchain.getHeight();
                if (height > blockchain.getHeight() + 1) {
                    Logger.logMessage("Rollback height " + (height - 1) + " exceeds current blockchain height of " + blockchain.getHeight() + ", no scan needed");
                    pstmtDone.executeUpdate();
                    Db.db.commitTransaction();
                    return;
                }
                if (height == 0) {
                    Logger.logDebugMessage("Dropping all full text search indexes");
                    FullTextTrigger.dropAll(con);
                }
                for (DerivedDbTable table : derivedTables) {
                    if (height == 0) {
                        table.truncate();
                    } else {
                        table.rollback(height - 1);
                    }
                }
                Db.db.clearCache();
                Db.db.commitTransaction();
                Logger.logDebugMessage("Rolled back derived tables");
                BlockImpl currentBlock = BlockDb.findBlockAtHeight(height);
                blockListeners.notify(currentBlock, Event.RESCAN_BEGIN);
                long currentBlockId = currentBlock.getId();
                if (height == 0) {
                    blockchain.setLastBlock(currentBlock); // special case to avoid no last block
                    Account.addOrGetAccount(Genesis.CREATOR_ID).apply(Genesis.CREATOR_PUBLIC_KEY);
                } else {
                    blockchain.setLastBlock(BlockDb.findBlockAtHeight(height - 1));
                }
                if (shutdown) {
                    Logger.logMessage("Scan will be performed at next start");
                    new Thread(() -> System.exit(0)).start();
                    return;
                }
                int pstmtSelectIndex = 1;
                if (height > 0) {
                    pstmtSelect.setInt(pstmtSelectIndex++, height);
                }
                long dbId = Long.MIN_VALUE;
                boolean hasMore = true;
                outer:
                while (hasMore) {
                    hasMore = false;
                    pstmtSelect.setLong(pstmtSelectIndex, dbId);

                    try (ResultSet rs = pstmtSelect.executeQuery()) {
                        while (rs.next()) {
                            try {
                                dbId = rs.getLong("db_id");
                                currentBlock = BlockDb.loadBlock(con, rs, true);
                                currentBlock.loadTransactions();
                                if (currentBlock.getId() != currentBlockId || currentBlock.getHeight() > blockchain.getHeight() + 1) {
                                    throw new PrizmException.NotValidException("Database blocks in the wrong order!");
                                }
                                Map<TransactionType, Map<String, Integer>> duplicates = new HashMap<>();

                                if (validate && currentBlockId != Genesis.GENESIS_BLOCK_ID) {
                                    int curTime = Prizm.getEpochTime();
                                    validate(currentBlock, blockchain.getLastBlock(), curTime);
                                    byte[] blockBytes = currentBlock.bytes();
                                    JSONObject blockJSON = (JSONObject) JSONValue.parse(currentBlock.getJSONObject().toJSONString());
                                    if (!Arrays.equals(blockBytes, BlockImpl.parseBlock(blockJSON).bytes())) {
                                        throw new PrizmException.NotValidException("Block JSON cannot be parsed back to the same block");
                                    }

                                    validateTransactions(currentBlock, blockchain.getLastBlock(), curTime, duplicates, true);
                                    for (TransactionImpl transaction : currentBlock.getTransactions()) {
                                        byte[] transactionBytes = transaction.bytes();
                                        if (currentBlock.getHeight() > Constants.NQT_BLOCK
                                                && !Arrays.equals(transactionBytes, TransactionImpl.newTransactionBuilder(transactionBytes).build().bytes())) {
                                            throw new PrizmException.NotValidException("Transaction bytes cannot be parsed back to the same transaction: "
                                                    + transaction.getJSONObject().toJSONString());
                                        }
                                        JSONObject transactionJSON = (JSONObject) JSONValue.parse(transaction.getJSONObject().toJSONString());
                                        if (!Arrays.equals(transactionBytes, TransactionImpl.newTransactionBuilder(transactionJSON).build().bytes())) {
                                            throw new PrizmException.NotValidException("Transaction JSON cannot be parsed back to the same transaction: "
                                                    + transaction.getJSONObject().toJSONString());
                                        }
                                    }
                                }
                                blockListeners.notify(currentBlock, Event.BEFORE_BLOCK_ACCEPT);
                                blockchain.setLastBlock(currentBlock);
                                accept(currentBlock, duplicates);
                                currentBlockId = currentBlock.getNextBlockId();
                                Db.db.clearCache();
                                Db.db.commitTransaction();
                                blockListeners.notify(currentBlock, Event.AFTER_BLOCK_ACCEPT);
                            } catch (PrizmException | RuntimeException e) {
                                Db.db.rollbackTransaction();
                                Logger.logDebugMessage(e.toString(), e);
                                Logger.logDebugMessage("Applying block " + Long.toUnsignedString(currentBlockId) + " at height "
                                        + (currentBlock == null ? 0 : currentBlock.getHeight()) + " failed, deleting from database");
                                BlockImpl lastBlock = BlockDb.deleteBlocksFrom(currentBlockId);
                                blockchain.setLastBlock(lastBlock);
                                popOffTo(lastBlock);
                                break outer;
                            }
                            blockListeners.notify(currentBlock, Event.BLOCK_SCANNED);
                            hasMore = true;
                        }
                        dbId = dbId + 1;
                    }
                }
                if (height == 0) {
                    for (DerivedDbTable table : derivedTables) {
                        table.createSearchIndex(con);
                    }
                }
                pstmtDone.executeUpdate();
                Db.db.commitTransaction();
                blockListeners.notify(currentBlock, Event.RESCAN_END);
                Logger.logMessage("...done at height " + blockchain.getHeight());
                if (height == 0 && validate) {
                    Logger.logMessage("SUCCESSFULLY PERFORMED FULL RESCAN WITH VALIDATION");
                }
                lastRestoreTime = 0;
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            } finally {
                isScanning = false;
            }
        } finally {
            blockchain.writeUnlock();
        }
    }

    // --- PRIZM ENGINE ---
    // [ONLY ONE]
    private List<TransactionImpl> prizmGenerateParamining(List<ParaBlock.Payout> payouts, int timestamp) throws PrizmException.NotValidException, PrizmException.ValidationException {
        if (payouts == null || payouts.isEmpty()) {
            return new ArrayList<>();
        }
        ArrayList<TransactionImpl> transactions = new ArrayList<>();
        for (ParaBlock.Payout payout : payouts) {
            JSONObject prizmJSONInfo = new JSONObject();
            prizmJSONInfo.put(Constants.IN_BLOCK_ID, payout.getBlockID());
            prizmJSONInfo.put(Constants.IN_BLOCK_HEIGHT, payout.getHeight());
            if (payout.getTxID() != null) {
                prizmJSONInfo.put(Constants.IN_TRANSACT_ID, payout.getTxID());
            }
            prizmJSONInfo.put(Constants.RANDOM, Math.random());
            Appendix.Message infogen = new Appendix.Message(prizmJSONInfo.toString(), true);
            try {
                if (payout.getAmount() > 0l) {
                    final TransactionImpl transactionX = new TransactionImpl.BuilderImpl((byte) 1, Genesis.CREATOR_PUBLIC_KEY,
                            payout.getAmount(), 0, (short) 720,
                            Attachment.ORDINARY_PAYMENT)
                            .recipientId(payout.getToID())
                            .appendix(infogen)
                            .timestamp(timestamp)
                            .build(Constants.GENESIS_SECRET_PHRASE);

//                final Transaction transaction0 = Prizm.newTransactionBuilder(Genesis.CREATOR_PUBLIC_KEY,
//                        payout.getAmount(), 0, (short) 720, 
//                        Attachment.ORDINARY_PAYMENT)
//                        .recipientId(payout.getToID())
//                        .appendix(infogen)
//                        .build(Constants.GENESIS_SECRET_PHRASE);
                    transactions.add(transactionX);
//                Prizm.getTransactionProcessor().broadcast(transaction0);
                }
            } catch (PrizmException.ValidationException ex) {
//                System.out.println("PrGenTrans EX: "+ex.toString());
                throw ex;
            }
        }
        return transactions;
    }

    private List<TransactionImpl> prizmCreator(int currentBlockTimestamp) throws PrizmException.NotValidException, PrizmException.ValidationException {
        ArrayList<TransactionImpl> emptyList = new ArrayList<>();
//        BlockImpl block = BlockchainImpl.getInstance().getBlockAtHeight(blockIn.getHeight() - 1);
        BlockImpl block = BlockchainImpl.getInstance().getLastBlock();
        ParaBlock paraBlock = new ParaBlock();
        if (block != null) {
            paraBlock.setID(block.getId());
            paraBlock.setGeneratorID(block.getGeneratorId());
            paraBlock.setFee(block.getTotalFeeNQT());
            paraBlock.setHeight(block.getHeight());
            paraBlock.setStamp(block.getTimestamp());
        }
        if (block != null && block.getTransactions() != null) {
            for (TransactionImpl blockTransaction : block.getTransactions()) {
                TransactionImpl trimpl = BlockchainImpl.getInstance().getTransaction(blockTransaction.getId());
                if (trimpl != null && trimpl.getHeight() != block.getHeight()) {
//                    System.out.println("+++ FUZZ: " + trimpl.getHeight() + " ORIG: " + block.getHeight());
                    continue;
                }
                
                if (Arrays.equals(blockTransaction.getSenderPublicKey(), Genesis.CREATOR_PUBLIC_KEY)) {
                    continue;
                }
                try {
                    ParaBlock.Transaction trx = ParaEngine.convert(blockTransaction, block.getHeight());
                    paraBlock.getTransactions().add(trx);
                } catch (ParaMiningException ex) {
                }
            }
        }
//        if (paraBlock.getTransactions().isEmpty()) return;
        try {
            List<ParaBlock.Payout> payouts = Prizm.para().check( (paraBlock != null && paraBlock.getTransactions() != null && paraBlock.getTransactions().size() > 0) ? paraBlock : null, block.getHeight(), paraBlock);
            return prizmGenerateParamining(payouts, currentBlockTimestamp);
        } catch (Exception ex) {
            return emptyList;
        }
    }

    public void prizmCheker(BlockImpl block) throws BlockNotAcceptedException {
        ////// ----- proverka bloka na validnost transacci
        /// List<ParaBlock.Transaction> pmTransactions = new ArrayList<ParaBlock.Transaction>();

        ParaBlock pmBlock = new ParaBlock();
        if (block != null && block.getTransactions() != null) {
            for (TransactionImpl transaction : block.getTransactions()) {
                TransactionImpl trimpl = BlockchainImpl.getInstance().getTransaction(transaction.getId());
                if (trimpl != null && trimpl.getHeight() != block.getHeight()) {
//                    System.out.println("--- FUZZ: "+trimpl.getHeight()+" ORIG: "+block.getHeight());
                    continue;
                }
                if (transaction.getSenderId() != Genesis.CREATOR_ID && transaction.getFeeNQT() < Prizm.para().getFixedFee(transaction.getAmountNQT())) {
                    throw new BlockNotAcceptedException(" Block not accept: fee is too low", block);
                }
                JSONObject json = null;
                if (!transaction.verifySignature()) {
                    throw new BlockNotAcceptedException(" Block not accept, tx not verifySignature", block);
                }
                ParaBlock.Transaction pmTransacnion = null;
                try {
                    if ((pmTransacnion = ParaEngine.convert(transaction, block.getHeight())) == null) {
                        throw new BlockNotAcceptedException("NULL Transaction in Block", block);
                    }
                } catch (ParaMiningException ex) {
                    throw new BlockNotAcceptedException("Invalid Transaction in Block", block);
                }
                pmBlock.getTransactions().add(pmTransacnion);
            }
            pmBlock.setID(block.getId());
            pmBlock.setFee(block.getTotalFeeNQT());
            pmBlock.setStamp(block.getTimestamp());
            pmBlock.setHeight(block.getHeight());
            pmBlock.setGeneratorID(block.getGeneratorId());
        }
//        if (pmBlock.getTransactions().size() > 0) {
        try {
            List<ParaBlock.Payout> payouts = Prizm.para().check((block != null && block.getTransactions() != null && block.getTransactions().size() > 0) ? pmBlock : null, block.getHeight(), pmBlock);
        } catch (ParaMiningException ex) {
            java.util.logging.Logger.getLogger(BlockchainProcessorImpl.class.getName()).log(Level.SEVERE, null, ex);
            throw new BlockNotAcceptedException(" Block not accept, tx not verifySignature", block);
        }
//        }

////----------------------------------------
    }

}
