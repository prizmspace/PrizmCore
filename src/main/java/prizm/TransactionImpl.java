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
import prizm.db.DbKey;
import prizm.util.Convert;
import prizm.util.Filter;
import prizm.util.Logger;
import org.json.simple.JSONObject;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class TransactionImpl implements Transaction {

    public static final class BuilderImpl implements Builder {

        private final short deadline;
        private final byte[] senderPublicKey;
        private final long amountNQT;
        private final long feeNQT;
        private final TransactionType type;
        private final byte version;
        private Attachment.AbstractAttachment attachment;

        private long recipientId;
        private byte[] referencedTransactionFullHash;
        private byte[] signature;
        private Appendix.Message message;
        private Appendix.EncryptedMessage encryptedMessage;
        private Appendix.EncryptToSelfMessage encryptToSelfMessage;
        private Appendix.PublicKeyAnnouncement publicKeyAnnouncement;
        private Appendix.PrunablePlainMessage prunablePlainMessage;
        private Appendix.PrunableEncryptedMessage prunableEncryptedMessage;
        private long blockId;
        private int height = Integer.MAX_VALUE;
        private long id;
        private long senderId;
        private int timestamp = Integer.MAX_VALUE;
        private int blockTimestamp = -1;
        private byte[] fullHash;
        private boolean ecBlockSet = false;
        private int ecBlockHeight;
        private long ecBlockId;
        private short index = -1;

        public BuilderImpl(byte version, byte[] senderPublicKey, long amountNQT, long feeNQT, short deadline,
                Attachment.AbstractAttachment attachment) {
            this.version = version;
            this.deadline = deadline;
            this.senderPublicKey = senderPublicKey;
            this.amountNQT = amountNQT;
            this.feeNQT = feeNQT;
            this.attachment = attachment;
            this.type = attachment.getTransactionType();
        }

        @Override
        public TransactionImpl build(String secretPhrase) throws PrizmException.NotValidException {
            if (timestamp == Integer.MAX_VALUE) {
                timestamp = Prizm.getEpochTime();
            }
            if (!ecBlockSet) {
                Block ecBlock = BlockchainImpl.getInstance().getECBlock(timestamp);
                this.ecBlockHeight = ecBlock.getHeight();
                this.ecBlockId = ecBlock.getId();
            }
            return new TransactionImpl(this, secretPhrase);
        }

        @Override
        public TransactionImpl build() throws PrizmException.NotValidException {
            return build(null);
        }

        public BuilderImpl recipientId(long recipientId) {
            this.recipientId = recipientId;
            return this;
        }

        @Override
        public BuilderImpl referencedTransactionFullHash(String referencedTransactionFullHash) {
            this.referencedTransactionFullHash = Convert.parseHexString(referencedTransactionFullHash);
            return this;
        }

        BuilderImpl referencedTransactionFullHash(byte[] referencedTransactionFullHash) {
            this.referencedTransactionFullHash = referencedTransactionFullHash;
            return this;
        }

        BuilderImpl appendix(Attachment.AbstractAttachment attachment) {
            this.attachment = attachment;
            return this;
        }

        @Override
        public BuilderImpl appendix(Appendix.Message message) {
            this.message = message;
            return this;
        }

        @Override
        public BuilderImpl appendix(Appendix.EncryptedMessage encryptedMessage) {
            this.encryptedMessage = encryptedMessage;
            return this;
        }

        @Override
        public BuilderImpl appendix(Appendix.EncryptToSelfMessage encryptToSelfMessage) {
            this.encryptToSelfMessage = encryptToSelfMessage;
            return this;
        }

        @Override
        public BuilderImpl appendix(Appendix.PublicKeyAnnouncement publicKeyAnnouncement) {
            this.publicKeyAnnouncement = publicKeyAnnouncement;
            return this;
        }

        @Override
        public BuilderImpl appendix(Appendix.PrunablePlainMessage prunablePlainMessage) {
            this.prunablePlainMessage = prunablePlainMessage;
            return this;
        }

        @Override
        public BuilderImpl appendix(Appendix.PrunableEncryptedMessage prunableEncryptedMessage) {
            this.prunableEncryptedMessage = prunableEncryptedMessage;
            return this;
        }

        @Override
        public BuilderImpl timestamp(int timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        @Override
        public BuilderImpl ecBlockHeight(int height) {
            this.ecBlockHeight = height;
            this.ecBlockSet = true;
            return this;
        }

        @Override
        public BuilderImpl ecBlockId(long blockId) {
            this.ecBlockId = blockId;
            this.ecBlockSet = true;
            return this;
        }

        BuilderImpl id(long id) {
            this.id = id;
            return this;
        }

        BuilderImpl signature(byte[] signature) {
            this.signature = signature;
            return this;
        }

        BuilderImpl blockId(long blockId) {
            this.blockId = blockId;
            return this;
        }

        BuilderImpl height(int height) {
            this.height = height;
            return this;
        }

        BuilderImpl senderId(long senderId) {
            this.senderId = senderId;
            return this;
        }

        BuilderImpl fullHash(byte[] fullHash) {
            this.fullHash = fullHash;
            return this;
        }

        BuilderImpl blockTimestamp(int blockTimestamp) {
            this.blockTimestamp = blockTimestamp;
            return this;
        }

        BuilderImpl index(short index) {
            this.index = index;
            return this;
        }

    }

    private final short deadline;
    private volatile byte[] senderPublicKey;
    private final long recipientId;
    private final long amountNQT;
    private final long feeNQT;
    private final byte[] referencedTransactionFullHash;
    private final TransactionType type;
    private final int ecBlockHeight;
    private final long ecBlockId;
    private final byte version;
    private final int timestamp;
    private final byte[] signature;
    private final Attachment.AbstractAttachment attachment;
    private final Appendix.Message message;
    private final Appendix.EncryptedMessage encryptedMessage;
    private final Appendix.EncryptToSelfMessage encryptToSelfMessage;
    private final Appendix.PublicKeyAnnouncement publicKeyAnnouncement;
    private final Appendix.PrunablePlainMessage prunablePlainMessage;
    private final Appendix.PrunableEncryptedMessage prunableEncryptedMessage;

    private final List<Appendix.AbstractAppendix> appendages;
    private final int appendagesSize;

    private volatile int height = Integer.MAX_VALUE;
    private volatile long blockId;
    private volatile BlockImpl block;
    private volatile int blockTimestamp = -1;
    private volatile short index = -1;
    private volatile long id;
    private volatile String stringId;
    private volatile long senderId;
    private volatile byte[] fullHash;
    private volatile DbKey dbKey;
    private volatile byte[] bytes = null;

    private TransactionImpl(BuilderImpl builder, String secretPhrase) throws PrizmException.NotValidException {

        this.timestamp = builder.timestamp;
        this.deadline = builder.deadline;
        this.senderPublicKey = builder.senderPublicKey;
        this.recipientId = builder.recipientId;
        this.amountNQT = builder.amountNQT;
        this.referencedTransactionFullHash = builder.referencedTransactionFullHash;
        this.type = builder.type;
        this.version = builder.version;
        this.blockId = builder.blockId;
        this.height = builder.height;
        this.index = builder.index;
        this.id = builder.id;
        this.senderId = builder.senderId;
        this.blockTimestamp = builder.blockTimestamp;
        this.fullHash = builder.fullHash;
        this.ecBlockHeight = builder.ecBlockHeight;
        this.ecBlockId = builder.ecBlockId;

        List<Appendix.AbstractAppendix> list = new ArrayList<>();
        if ((this.attachment = builder.attachment) != null) {
            list.add(this.attachment);
        }
        if ((this.message = builder.message) != null) {
            list.add(this.message);
        }
        if ((this.encryptedMessage = builder.encryptedMessage) != null) {
            list.add(this.encryptedMessage);
        }
        if ((this.publicKeyAnnouncement = builder.publicKeyAnnouncement) != null) {
            list.add(this.publicKeyAnnouncement);
        }
        if ((this.encryptToSelfMessage = builder.encryptToSelfMessage) != null) {
            list.add(this.encryptToSelfMessage);
        }
        if ((this.prunablePlainMessage = builder.prunablePlainMessage) != null) {
            list.add(this.prunablePlainMessage);
        }
        if ((this.prunableEncryptedMessage = builder.prunableEncryptedMessage) != null) {
            list.add(this.prunableEncryptedMessage);
        }
        this.appendages = Collections.unmodifiableList(list);
        int appendagesSize = 0;
        for (Appendix appendage : appendages) {
            if (secretPhrase != null && appendage instanceof Appendix.Encryptable) {
                ((Appendix.Encryptable) appendage).encrypt(secretPhrase);
            }
            appendagesSize += appendage.getSize();
        }
        this.appendagesSize = appendagesSize;
        if (builder.feeNQT <= 0 || (Constants.correctInvalidFees && builder.signature == null)) {
            int effectiveHeight = (height < Integer.MAX_VALUE ? height : Prizm.getBlockchain().getHeight());
            long minFee = getMinimumFeeNQT(effectiveHeight);
            feeNQT = Math.max(minFee, builder.feeNQT);
        } else {
            feeNQT = builder.feeNQT;
        }

        //byte[] tempsigDlayProverki = Crypto.sign(bytes(), secretPhrase);
        
        if (builder.signature != null && secretPhrase != null) {
            throw new PrizmException.NotValidException("Transaction is already signed");
        } else if (builder.signature != null) {
            this.signature = builder.signature;
        } else if (secretPhrase != null) {
            if (getSenderPublicKey() != null && !Arrays.equals(senderPublicKey, Crypto.getPublicKey(secretPhrase))) {
                if (!Arrays.equals(senderPublicKey, Genesis.CREATOR_PUBLIC_KEY)) {
                    throw new PrizmException.NotValidException("Secret phrase doesn't match transaction sender public key");
                }
            }
            signature = Crypto.sign(bytes(), secretPhrase);
            bytes = null;
        } else {
            signature = null;
        }

    }

    @Override
    public short getDeadline() {
        return deadline;
    }

    @Override
    public byte[] getSenderPublicKey() {
        if (senderPublicKey == null) {
            senderPublicKey = Account.getPublicKey(senderId);
        }
        return senderPublicKey;
    }

    @Override
    public long getRecipientId() {
        return recipientId;
    }

    @Override
    public long getAmountNQT() {
        return amountNQT;
    }

    @Override
    public long getFeeNQT() {
        return feeNQT;
    }

    long[] getBackFees() {
        return type.getBackFees(this);
    }

    @Override
    public String getReferencedTransactionFullHash() {
        return Convert.toHexString(referencedTransactionFullHash);
    }

    byte[] referencedTransactionFullHash() {
        return referencedTransactionFullHash;
    }

    @Override
    public int getHeight() {
        return height;
    }

    void setHeight(int height) {
        this.height = height;
    }

    @Override
    public byte[] getSignature() {
        return signature;
    }

    @Override
    public TransactionType getType() {
        return type;
    }

    @Override
    public byte getVersion() {
        return version;
    }

    @Override
    public long getBlockId() {
        return blockId;
    }

    @Override
    public BlockImpl getBlock() {
        if (block == null && blockId != 0) {
            block = BlockchainImpl.getInstance().getBlock(blockId);
        }
        return block;
    }

    void setBlock(BlockImpl block) {
        this.block = block;
        this.blockId = block.getId();
        this.height = block.getHeight();
        this.blockTimestamp = block.getTimestamp();
    }

    void unsetBlock() {
        this.block = null;
        this.blockId = 0;
        this.blockTimestamp = -1;
        this.index = -1;
        // must keep the height set, as transactions already having been included in a popped-off block before
        // get priority when sorted for inclusion in a new block
    }

    @Override
    public short getIndex() {
        if (index == -1) {
            throw new IllegalStateException("Transaction index has not been set");
        }
        return index;
    }

    void setIndex(int index) {
        this.index = (short) index;
    }

    @Override
    public int getTimestamp() {
        return timestamp;
    }

    @Override
    public int getBlockTimestamp() {
        return blockTimestamp;
    }

    @Override
    public int getExpiration() {
        return timestamp + deadline * 60;
    }

    @Override
    public Attachment.AbstractAttachment getAttachment() {
        attachment.loadPrunable(this);
        return attachment;
    }

    @Override
    public List<Appendix.AbstractAppendix> getAppendages() {
        return getAppendages(false);
    }

    @Override
    public List<Appendix.AbstractAppendix> getAppendages(boolean includeExpiredPrunable) {
        for (Appendix.AbstractAppendix appendage : appendages) {
            appendage.loadPrunable(this, includeExpiredPrunable);
        }
        return appendages;
    }

    @Override
    public List<Appendix> getAppendages(Filter<Appendix> filter, boolean includeExpiredPrunable) {
        List<Appendix> result = new ArrayList<>();
        appendages.forEach(appendix -> {
            if (filter.ok(appendix)) {
                appendix.loadPrunable(this, includeExpiredPrunable);
                result.add(appendix);
            }
        });
        return result;
    }

    @Override
    public long getId() {
        if (id == 0) {
            if (signature == null) {
                throw new IllegalStateException("Transaction is not signed yet");
            }
            if (useNQT()) {
                byte[] data = zeroSignature(getBytes());
                byte[] signatureHash = Crypto.sha256().digest(signature);
                MessageDigest digest = Crypto.sha256();
                digest.update(data);
                fullHash = digest.digest(signatureHash);
            } else {
                fullHash = Crypto.sha256().digest(bytes());
            }
            BigInteger bigInteger = new BigInteger(1, new byte[]{fullHash[7], fullHash[6], fullHash[5], fullHash[4], fullHash[3], fullHash[2], fullHash[1], fullHash[0]});
            id = bigInteger.longValue();
            stringId = bigInteger.toString();
        }
        return id;
    }

    @Override
    public String getStringId() {
        if (stringId == null) {
            getId();
            if (stringId == null) {
                stringId = Long.toUnsignedString(id);
            }
        }
        return stringId;
    }

    @Override
    public String getFullHash() {
        return Convert.toHexString(fullHash());
    }

    byte[] fullHash() {
        if (fullHash == null) {
            getId();
        }
        return fullHash;
    }

    @Override
    public long getSenderId() {
        if (senderId == 0) {
            senderId = Account.getId(getSenderPublicKey());
        }
        return senderId;
    }

    DbKey getDbKey() {
        if (dbKey == null) {
            dbKey = TransactionProcessorImpl.getInstance().unconfirmedTransactionDbKeyFactory.newKey(getId());
        }
        return dbKey;
    }

    @Override
    public Appendix.Message getMessage() {
        return message;
    }

    @Override
    public Appendix.EncryptedMessage getEncryptedMessage() {
        return encryptedMessage;
    }

    @Override
    public Appendix.EncryptToSelfMessage getEncryptToSelfMessage() {
        return encryptToSelfMessage;
    }

    Appendix.PublicKeyAnnouncement getPublicKeyAnnouncement() {
        return publicKeyAnnouncement;
    }

    @Override
    public Appendix.PrunablePlainMessage getPrunablePlainMessage() {
        if (prunablePlainMessage != null) {
            prunablePlainMessage.loadPrunable(this);
        }
        return prunablePlainMessage;
    }

    boolean hasPrunablePlainMessage() {
        return prunablePlainMessage != null;
    }

    @Override
    public Appendix.PrunableEncryptedMessage getPrunableEncryptedMessage() {
        if (prunableEncryptedMessage != null) {
            prunableEncryptedMessage.loadPrunable(this);
        }
        return prunableEncryptedMessage;
    }

    boolean hasPrunableEncryptedMessage() {
        return prunableEncryptedMessage != null;
    }

    public byte[] getBytes() {
        return Arrays.copyOf(bytes(), bytes.length);
    }

    byte[] bytes() {
        if (bytes == null) {
            try {
                final int BUFFER_SIZE = getSize();
                ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.put(type.getType());
                buffer.put((byte) ((version << 4) | type.getSubtype()));
                buffer.putInt(timestamp);
                buffer.putShort(deadline);
                buffer.put(getSenderPublicKey());
                buffer.putLong(type.canHaveRecipient() ? recipientId : Genesis.CREATOR_ID);

                if (useNQT()) {
                    buffer.putLong(amountNQT);
                    buffer.putLong(feeNQT);
                    if (referencedTransactionFullHash != null) {
                        buffer.put(referencedTransactionFullHash);
                    } else {
                        buffer.put(new byte[32]);
                    }
                } else {
                    buffer.putInt((int) (amountNQT / Constants.ONE_PRIZM));
                    buffer.putInt((int) (feeNQT / Constants.ONE_PRIZM));
                    if (referencedTransactionFullHash != null) {
                        buffer.putLong(Convert.fullHashToId(referencedTransactionFullHash));
                    } else {
                        buffer.putLong(0L);
                    }
                }
                buffer.put(signature != null ? signature : new byte[64]);
                if (version > 0) {
                    buffer.putInt(getFlags());
                    buffer.putInt(ecBlockHeight);
                    buffer.putLong(ecBlockId);
                }
                for (Appendix appendage : appendages) {
                    appendage.putBytes(buffer);
                }
                bytes = buffer.array();
            } catch (RuntimeException e) {
                if (signature != null) {
                    Logger.logDebugMessage("Failed to get transaction bytes for transaction: " + getJSONObject().toJSONString());
                    e.printStackTrace();
                }
                throw e;
            }
        }
        return bytes;
    }

    static TransactionImpl.BuilderImpl newTransactionBuilder(byte[] bytes) throws PrizmException.NotValidException {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            byte type = buffer.get();
            byte subtype = buffer.get();
            byte version = (byte) ((subtype & 0xF0) >> 4);
            subtype = (byte) (subtype & 0x0F);
            int timestamp = buffer.getInt();
            short deadline = buffer.getShort();
            byte[] senderPublicKey = new byte[32];
            buffer.get(senderPublicKey);
            long recipientId = buffer.getLong();
            long amountNQT = buffer.getLong();
            long feeNQT = buffer.getLong();
            byte[] referencedTransactionFullHash = new byte[32];
            buffer.get(referencedTransactionFullHash);
            referencedTransactionFullHash = Convert.emptyToNull(referencedTransactionFullHash);
            byte[] signature = new byte[64];
            buffer.get(signature);
            signature = Convert.emptyToNull(signature);
            int flags = 0;
            int ecBlockHeight = 0;
            long ecBlockId = 0;
            if (version > 0) {
                flags = buffer.getInt();
                ecBlockHeight = buffer.getInt();
                ecBlockId = buffer.getLong();
            }
            TransactionType transactionType = TransactionType.findTransactionType(type, subtype);
            TransactionImpl.BuilderImpl builder = new BuilderImpl(version, senderPublicKey, amountNQT, feeNQT,
                    deadline, transactionType.parseAttachment(buffer, version))
                    .timestamp(timestamp)
                    .referencedTransactionFullHash(referencedTransactionFullHash)
                    .signature(signature)
                    .ecBlockHeight(ecBlockHeight)
                    .ecBlockId(ecBlockId);
            if (transactionType.canHaveRecipient()) {
                builder.recipientId(recipientId);
            }
            int position = 1;
            if ((flags & position) != 0 || (version == 0 && transactionType == TransactionType.Messaging.ARBITRARY_MESSAGE)) {
                builder.appendix(new Appendix.Message(buffer, version));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                builder.appendix(new Appendix.EncryptedMessage(buffer, version));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                builder.appendix(new Appendix.PublicKeyAnnouncement(buffer, version));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                builder.appendix(new Appendix.EncryptToSelfMessage(buffer, version));
            }
            position <<= 1; // Ignore Phasing flag
            position <<= 1;
            if ((flags & position) != 0) {
                builder.appendix(new Appendix.PrunablePlainMessage(buffer, version));
            }
            position <<= 1;
            if ((flags & position) != 0) {
                builder.appendix(new Appendix.PrunableEncryptedMessage(buffer, version));
            }
            if (buffer.hasRemaining()) {
                throw new PrizmException.NotValidException("Transaction bytes too long, " + buffer.remaining() + " extra bytes");
            }
            return builder;
        } catch (PrizmException.NotValidException | RuntimeException e) {
            Logger.logDebugMessage("Failed to parse transaction bytes: " + Convert.toHexString(bytes));
            e.printStackTrace();
            throw e;
        }
    }

    static TransactionImpl.BuilderImpl newTransactionBuilder(byte[] bytes, JSONObject prunableAttachments) throws PrizmException.NotValidException {
        BuilderImpl builder = newTransactionBuilder(bytes);
        if (prunableAttachments != null) {
            Appendix.PrunablePlainMessage prunablePlainMessage = Appendix.PrunablePlainMessage.parse(prunableAttachments);
            if (prunablePlainMessage != null) {
                builder.appendix(prunablePlainMessage);
            }
            Appendix.PrunableEncryptedMessage prunableEncryptedMessage = Appendix.PrunableEncryptedMessage.parse(prunableAttachments);
            if (prunableEncryptedMessage != null) {
                builder.appendix(prunableEncryptedMessage);
            }
        }
        return builder;
    }

    public byte[] getUnsignedBytes() {
        return zeroSignature(getBytes());
    }

    @Override
    public JSONObject getJSONObject() {
        JSONObject json = new JSONObject();
        json.put("type", type.getType());
        json.put("subtype", type.getSubtype());
        json.put("timestamp", timestamp);
        json.put("deadline", deadline);
        json.put("senderPublicKey", Convert.toHexString(getSenderPublicKey()));
        if (type.canHaveRecipient()) {
            json.put("recipient", Long.toUnsignedString(recipientId));
        }
        json.put("amountNQT", amountNQT);
        json.put("feeNQT", feeNQT);
        if (referencedTransactionFullHash != null) {
            json.put("referencedTransactionFullHash", Convert.toHexString(referencedTransactionFullHash));
        }
        json.put("ecBlockHeight", ecBlockHeight);
        json.put("ecBlockId", Long.toUnsignedString(ecBlockId));
        json.put("signature", Convert.toHexString(signature));
        JSONObject attachmentJSON = new JSONObject();
        for (Appendix.AbstractAppendix appendage : appendages) {
            appendage.loadPrunable(this);
            attachmentJSON.putAll(appendage.getJSONObject());
        }
        if (!attachmentJSON.isEmpty()) {
            json.put("attachment", attachmentJSON);
        }
        json.put("version", version);
        return json;
    }

    @Override
    public JSONObject getPrunableAttachmentJSON() {
        JSONObject prunableJSON = null;
        for (Appendix.AbstractAppendix appendage : appendages) {
            if (appendage instanceof Appendix.Prunable) {
                appendage.loadPrunable(this);
                if (prunableJSON == null) {
                    prunableJSON = appendage.getJSONObject();
                } else {
                    prunableJSON.putAll(appendage.getJSONObject());
                }
            }
        }
        return prunableJSON;
    }

    static TransactionImpl parseTransaction(JSONObject transactionData) throws PrizmException.NotValidException {
        TransactionImpl transaction = newTransactionBuilder(transactionData).build();
        if (transaction.getSignature() != null && !transaction.checkSignature()) {
            throw new PrizmException.NotValidException("Invalid transaction signature for transaction " + transaction.getJSONObject().toJSONString());
        }
        return transaction;
    }

    static TransactionImpl.BuilderImpl newTransactionBuilder(JSONObject transactionData) throws PrizmException.NotValidException {
        try {
            byte type = ((Long) transactionData.get("type")).byteValue();
            byte subtype = ((Long) transactionData.get("subtype")).byteValue();
            int timestamp = ((Long) transactionData.get("timestamp")).intValue();
            short deadline = ((Long) transactionData.get("deadline")).shortValue();
            byte[] senderPublicKey = Convert.parseHexString((String) transactionData.get("senderPublicKey"));
            long amountNQT = Convert.parseLong(transactionData.get("amountNQT"));
            long feeNQT = Convert.parseLong(transactionData.get("feeNQT"));
            String referencedTransactionFullHash = (String) transactionData.get("referencedTransactionFullHash");
            byte[] signature = Convert.parseHexString((String) transactionData.get("signature"));
            Long versionValue = (Long) transactionData.get("version");
            byte version = versionValue == null ? 0 : versionValue.byteValue();
            JSONObject attachmentData = (JSONObject) transactionData.get("attachment");
            int ecBlockHeight = 0;
            long ecBlockId = 0;
            if (version > 0) {
                ecBlockHeight = ((Long) transactionData.get("ecBlockHeight")).intValue();
                ecBlockId = Convert.parseUnsignedLong((String) transactionData.get("ecBlockId"));
            }

            TransactionType transactionType = TransactionType.findTransactionType(type, subtype);
            if (transactionType == null) {
                throw new PrizmException.NotValidException("Invalid transaction type: " + type + ", " + subtype);
            }
            TransactionImpl.BuilderImpl builder = new BuilderImpl(version, senderPublicKey,
                    amountNQT, feeNQT, deadline,
                    transactionType.parseAttachment(attachmentData))
                    .timestamp(timestamp)
                    .referencedTransactionFullHash(referencedTransactionFullHash)
                    .signature(signature)
                    .ecBlockHeight(ecBlockHeight)
                    .ecBlockId(ecBlockId);
            if (transactionType.canHaveRecipient()) {
                long recipientId = Convert.parseUnsignedLong((String) transactionData.get("recipient"));
                builder.recipientId(recipientId);
            }
            if (attachmentData != null) {
                builder.appendix(Appendix.Message.parse(attachmentData));
                builder.appendix(Appendix.EncryptedMessage.parse(attachmentData));
                builder.appendix((Appendix.PublicKeyAnnouncement.parse(attachmentData)));
                builder.appendix(Appendix.EncryptToSelfMessage.parse(attachmentData));
                builder.appendix(Appendix.PrunablePlainMessage.parse(attachmentData));
                builder.appendix(Appendix.PrunableEncryptedMessage.parse(attachmentData));
            }
            return builder;
        } catch (PrizmException.NotValidException | RuntimeException e) {
            Logger.logDebugMessage("Failed to parse transaction: " + transactionData.toJSONString() + " trx="+transactionData.size());
            throw e;
        }
    }

    @Override
    public int getECBlockHeight() {
        return ecBlockHeight;
    }

    @Override
    public long getECBlockId() {
        return ecBlockId;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TransactionImpl && this.getId() == ((Transaction) o).getId();
    }

    @Override
    public int hashCode() {
        return (int) (getId() ^ (getId() >>> 32));
    }

    public boolean verifySignature() {
        return checkSignature() && Account.setOrVerify(getSenderId(), getSenderPublicKey());
    }

    private volatile boolean hasValidSignature = false;

    private boolean checkSignature() {
        if (!hasValidSignature) {
            hasValidSignature = signature != null && Crypto.verify(signature, zeroSignature(getBytes()), getSenderPublicKey(), useNQT());
        }
        return hasValidSignature;
    }

    private int getSize() {
        return signatureOffset() + 64 + (version > 0 ? 4 + 4 + 8 : 0) + appendagesSize;
    }

    @Override
    public int getFullSize() {
        int fullSize = getSize() - appendagesSize;
        for (Appendix.AbstractAppendix appendage : getAppendages()) {
            fullSize += appendage.getFullSize();
        }
        return fullSize;
    }

    private int signatureOffset() {
        return 1 + 1 + 4 + 2 + 32 + 8 + (useNQT() ? 8 + 8 + 32 : 4 + 4 + 8);
    }

    private boolean useNQT() {
//// ssss  WAS is DAS !!!!!       bilo 12908200 : 14271000
        return this.height > Constants.NQT_BLOCK
                && (this.timestamp > (Constants.isTestnet ? 4787757 - 100 : 4787757 - 100)
                || Prizm.getBlockchain().getHeight() >= Constants.NQT_BLOCK);
    }

    private byte[] zeroSignature(byte[] data) {
        int start = signatureOffset();
        for (int i = start; i < start + 64; i++) {
            data[i] = 0;
        }
        return data;
    }

    private int getFlags() {
        int flags = 0;
        int position = 1;
        if (message != null) {
            flags |= position;
        }
        position <<= 1;
        if (encryptedMessage != null) {
            flags |= position;
        }
        position <<= 1;
        if (publicKeyAnnouncement != null) {
            flags |= position;
        }
        position <<= 1;
        if (encryptToSelfMessage != null) {
            flags |= position;
        }
        position <<= 1; // We are still shifting position, but it's value is always zero
//        if (phasing != null) {
//            flags |= position;
//        }
        position <<= 1;
        if (prunablePlainMessage != null) {
            flags |= position;
        }
        position <<= 1;
        if (prunableEncryptedMessage != null) {
            flags |= position;
        }
        return flags;
    }

    @Override
    public void validate() throws PrizmException.ValidationException {

        int currentHeight = BlockchainImpl.getInstance().getHeight();
        if (ParaExcludes.check(getSenderId(), currentHeight)) {
            throw new PrizmException.NotValidException("This transaction can't be accepted (blacklisted)!");
        }
        
        if ((timestamp == 0 ? (deadline != 0 || feeNQT != 0) : (deadline < 1 || feeNQT < 0))
                || (feeNQT == 0 ? (getSenderId() != Genesis.CREATOR_ID) : (getSenderId() == Genesis.CREATOR_ID))
                || feeNQT > Constants.MAX_BALANCE_NQT
                || amountNQT < 0
                || amountNQT > Constants.MAX_BALANCE_NQT
                || type == null) {
            throw new PrizmException.NotValidException("Invalid transaction parameters:\n type: " + type + ", timestamp: " + timestamp
                    + ", deadline: " + deadline + ", fee: " + feeNQT + ", amount: " + amountNQT);
        }

        // No transaction can avoid validation
        if (currentHeight > Constants.CONTROL_TRX_TO_ORDINARY) {
            if (getType().getType() > 1) {
                throw new PrizmException.NotCurrentlyValidException("Invalid transaction type:" + getType().getName());
            }
            if (currentHeight > Constants.LAST_ALIASES_BLOCK) {
                if (getType().getType() == 1 && (getType().getSubtype() == 1 || getType().getSubtype() == 8)) { // 1.1 Alias Assignment 1.8 Alias Delete
                    throw new PrizmException.NotCurrentlyValidException("Invalid transaction subtype " + type.getSubtype() + " for transaction of type " + type.getName());
                }
            }
        }

        if (referencedTransactionFullHash != null && referencedTransactionFullHash.length != 32) {
            throw new PrizmException.NotValidException("Invalid referenced transaction full hash " + Convert.toHexString(referencedTransactionFullHash));
        }

        if (attachment == null || type != attachment.getTransactionType()) {
            throw new PrizmException.NotValidException("Invalid attachment " + attachment + " for transaction of type " + type);
        }

        if (!type.canHaveRecipient()) {
            if (recipientId != 0 || getAmountNQT() != 0) {
                throw new PrizmException.NotValidException("Transactions of this type must have recipient == 0, amount == 0");
            }
        }

        if (type.mustHaveRecipient() && version > 0) {
            if (recipientId == 0) {
                throw new PrizmException.NotValidException("Transactions of this type must have a valid recipient");
            }
        }

        for (Appendix.AbstractAppendix appendage : appendages) {
            appendage.loadPrunable(this);
            if (!appendage.verifyVersion(this.version)) {
                throw new PrizmException.NotValidException("Invalid attachment version " + appendage.getVersion()
                        + " for transaction version " + this.version);
            }
            appendage.validate(this);
        }

        if (getFullSize() > Constants.MAX_TRANSACTION_PAYLOAD_LENGTH) {
            throw new PrizmException.NotValidException("Transaction size " + getFullSize() + " exceeds maximum transaction payload size");
        }

        int blockchainHeight = Prizm.getBlockchain().getHeight();

        long minimumFeeNQT = getMinimumFeeNQT(blockchainHeight);
        if (feeNQT < minimumFeeNQT) {
            throw new PrizmException.NotCurrentlyValidException(String.format("Transaction fee %f coins less than minimum fee %f coins at height %d",
                    ((double) feeNQT) / Constants.ONE_PRIZM, ((double) minimumFeeNQT) / Constants.ONE_PRIZM, blockchainHeight));
        }
    }

    // returns false iff double spending
    boolean applyUnconfirmed() {
        Account senderAccount = Account.getAccount(getSenderId());
        return senderAccount != null && type.applyUnconfirmed(this, senderAccount);
    }

    void apply() {
        Account senderAccount = Account.getAccount(getSenderId());
        senderAccount.apply(getSenderPublicKey());
        Account recipientAccount = null;
        if (recipientId != 0) {
            recipientAccount = Account.getAccount(recipientId);
            if (recipientAccount == null) {
                recipientAccount = Account.addOrGetAccount(recipientId);
            }
        }

        for (Appendix.AbstractAppendix appendage : appendages) {
            appendage.loadPrunable(this);
            appendage.apply(this, senderAccount, recipientAccount);
        }
    }

    void undoUnconfirmed() {
        Account senderAccount = Account.getAccount(getSenderId());
        type.undoUnconfirmed(this, senderAccount);
    }

    boolean attachmentIsDuplicate(Map<TransactionType, Map<String, Integer>> duplicates, boolean atAcceptanceHeight) {
        if (atAcceptanceHeight) {
            // all are checked at acceptance height for block duplicates
            if (type.isBlockDuplicate(this, duplicates)) {
                return true;
            }
        }
        // non-phased at acceptance height, and phased at execution height
        return type.isDuplicate(this, duplicates);
    }

    boolean isUnconfirmedDuplicate(Map<TransactionType, Map<String, Integer>> duplicates) {
        return type.isUnconfirmedDuplicate(this, duplicates);
    }

    private long getMinimumFeeNQT(int blockchainHeight) {
        // Calculation depended on attachments, but we are not using payed attachments! Use constant instead.
        return Constants.MIN_FEE_NQT;
    }

}
