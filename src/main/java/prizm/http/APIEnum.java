/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016-2017 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package prizm.http;

import java.util.Base64;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public enum APIEnum {
    //To preserve compatibility, please add new APIs to the end of the enum.
    //When an API is deleted, set its name to empty string and handler to null.
    BROADCAST_TRANSACTION("broadcastTransaction", BroadcastTransaction.instance),
    CALCULATE_FULL_HASH("calculateFullHash", CalculateFullHash.instance),
    DECRYPT_FROM("decryptFrom", DecryptFrom.instance),
    DECODE_HALLMARK("decodeHallmark", DecodeHallmark.instance),
    DECODE_TOKEN("decodeToken", DecodeToken.instance),
    DECODE_Q_R_CODE("decodeQRCode", DecodeQRCode.instance),
    ENCODE_Q_R_CODE("encodeQRCode", EncodeQRCode.instance),
    ENCRYPT_TO("encryptTo", EncryptTo.instance),
    EVENT_REGISTER("eventRegister", EventRegister.instance),
    EVENT_WAIT("eventWait", EventWait.instance),
    GENERATE_TOKEN("generateToken", GenerateToken.instance),
    GET_ACCOUNT("getAccount", GetAccount.instance),
    GET_ACCOUNT_BLOCK_COUNT("getAccountBlockCount", GetAccountBlockCount.instance),
    GET_ACCOUNT_BLOCK_IDS("getAccountBlockIds", GetAccountBlockIds.instance),
    GET_ACCOUNT_BLOCKS("getAccountBlocks", GetAccountBlocks.instance),
    GET_ACCOUNT_HIERARCHY("getAccountHierarchy", GetAccountHierarchy.instance),
    GET_LOYALTY("getLoyalty", GetLoyalty.instance),
    GET_MOST_LOYAL_PARENT("getMostLoyalParent", GetMostLoyalParent.instance),
    GET_PARENT("getParent", GetParent.instance),
    IS_CHILD_OF("isChildOf", IsChildOf.instance),
    GET_ACCOUNT_CHILDREN("getAccountChildren", GetAccountChildren.instance),
    GET_ACCOUNT_ID("getAccountId", GetAccountId.instance),
    GET_ACCOUNT_LEDGER("getAccountLedger", GetAccountLedger.instance),
    GET_ACCOUNT_LEDGER_ENTRY("getAccountLedgerEntry", GetAccountLedgerEntry.instance),
    GET_ACCOUNT_PUBLIC_KEY("getAccountPublicKey", GetAccountPublicKey.instance),
    GET_ALIAS("getAlias", GetAlias.instance),
    GET_ALIAS_COUNT("getAliasCount", GetAliasCount.instance),
    GET_ALIASES("getAliases", GetAliases.instance),
    GET_ALIASES_LIKE("getAliasesLike", GetAliasesLike.instance),
    GET_BALANCE("getBalance", GetBalance.instance),
    GET_BLOCK("getBlock", GetBlock.instance),
    GET_BLOCK_ID("getBlockId", GetBlockId.instance),
    GET_BLOCKS("getBlocks", GetBlocks.instance),
    GET_BLOCKCHAIN_STATUS("getBlockchainStatus", GetBlockchainStatus.instance),
    GET_BLOCKCHAIN_TRANSACTIONS("getBlockchainTransactions", GetBlockchainTransactions.instance),
    GET_REFERENCING_TRANSACTIONS("getReferencingTransactions", GetReferencingTransactions.instance),
    GET_CONSTANTS("getConstants", GetConstants.instance),
    GET_GUARANTEED_BALANCE("getGuaranteedBalance", GetGuaranteedBalance.instance),
    GET_E_C_BLOCK("getECBlock", GetECBlock.instance),
    GET_INBOUND_PEERS("getInboundPeers", GetInboundPeers.instance),
    GET_PLUGINS("getPlugins", GetPlugins.instance),
    GET_MY_INFO("getMyInfo", GetMyInfo.instance),
    GET_PEER("getPeer", GetPeer.instance),
    GET_PEERS("getPeers", GetPeers.instance),
    GET_STATE("getState", GetState.instance),
    GET_TIME("getTime", GetTime.instance),
    GET_TRANSACTION("getTransaction", GetTransaction.instance),
    GET_TRANSACTION_BYTES("getTransactionBytes", GetTransactionBytes.instance),
    GET_UNCONFIRMED_TRANSACTION_IDS("getUnconfirmedTransactionIds", GetUnconfirmedTransactionIds.instance),
    GET_UNCONFIRMED_TRANSACTIONS("getUnconfirmedTransactions", GetUnconfirmedTransactions.instance),
    GET_EXPECTED_TRANSACTIONS("getExpectedTransactions", GetExpectedTransactions.instance),
    GET_PRUNABLE_MESSAGE("getPrunableMessage", GetPrunableMessage.instance),
    GET_PRUNABLE_MESSAGES("getPrunableMessages", GetPrunableMessages.instance),
    GET_ALL_PRUNABLE_MESSAGES("getAllPrunableMessages", GetAllPrunableMessages.instance),
    VERIFY_PRUNABLE_MESSAGE("verifyPrunableMessage", VerifyPrunableMessage.instance),
    LONG_CONVERT("longConvert", LongConvert.instance),
    HEX_CONVERT("hexConvert", HexConvert.instance),
    MARK_HOST("markHost", MarkHost.instance),
    PARSE_TRANSACTION("parseTransaction", ParseTransaction.instance),
    RS_CONVERT("rsConvert", RSConvert.instance),
    READ_MESSAGE("readMessage", ReadMessage.instance),
    SEND_MESSAGE("sendMessage", SendMessage.instance),
    SEND_MONEY("sendMoney", SendMoney.instance),
    SET_ACCOUNT_INFO("setAccountInfo", SetAccountInfo.instance),
    SET_ALIAS("setAlias", SetAlias.instance),
    DELETE_ALIAS("deleteAlias", DeleteAlias.instance),
    SIGN_TRANSACTION("signTransaction", SignTransaction.instance),
    START_FORGING("startForging", StartForging.instance),
    STOP_FORGING("stopForging", StopForging.instance),
    GET_FORGING("getForging", GetForging.instance),
    SEARCH_ACCOUNTS("searchAccounts", SearchAccounts.instance),
    CLEAR_UNCONFIRMED_TRANSACTIONS("clearUnconfirmedTransactions", ClearUnconfirmedTransactions.instance),
    REQUEUE_UNCONFIRMED_TRANSACTIONS("requeueUnconfirmedTransactions", RequeueUnconfirmedTransactions.instance),
    REBROADCAST_UNCONFIRMED_TRANSACTIONS("rebroadcastUnconfirmedTransactions", RebroadcastUnconfirmedTransactions.instance),
    GET_ALL_WAITING_TRANSACTIONS("getAllWaitingTransactions", GetAllWaitingTransactions.instance),
    GET_ALL_BROADCASTED_TRANSACTIONS("getAllBroadcastedTransactions", GetAllBroadcastedTransactions.instance),
    POP_OFF("popOff", PopOff.instance),
    SCAN("scan", Scan.instance),
    LUCENE_REINDEX("luceneReindex", LuceneReindex.instance),
    ADD_PEER("addPeer", AddPeer.instance),
    BLACKLIST_PEER("blacklistPeer", BlacklistPeer.instance),
    DUMP_PEERS("dumpPeers", DumpPeers.instance),
    GET_LOG("getLog", GetLog.instance),
    GET_STACK_TRACES("getStackTraces", GetStackTraces.instance),
    RETRIEVE_PRUNED_DATA("retrievePrunedData", RetrievePrunedData.instance),
    RETRIEVE_PRUNED_TRANSACTION("retrievePrunedTransaction", RetrievePrunedTransaction.instance),
    SET_LOGGING("setLogging", SetLogging.instance),
    TRIM_DERIVED_TABLES("trimDerivedTables", TrimDerivedTables.instance),
    HASH("hash", Hash.instance),
    FULL_HASH_TO_ID("fullHashToId", FullHashToId.instance),
    DOWNLOAD_PRUNABLE_MESSAGE("downloadPrunableMessage", DownloadPrunableMessage.instance),
    GET_SHARED_KEY("getSharedKey", GetSharedKey.instance),
    SET_API_PROXY_PEER("setAPIProxyPeer", SetAPIProxyPeer.instance),
    SEND_TRANSACTION("sendTransaction", SendTransaction.instance),
    BLACKLIST_API_PROXY_PEER("blacklistAPIProxyPeer", BlacklistAPIProxyPeer.instance),
    GET_SCHEDULED_TRANSACTIONS("getScheduledTransactions", GetScheduledTransactions.instance),
    DELETE_SCHEDULED_TRANSACTION("deleteScheduledTransaction", DeleteScheduledTransaction.instance),
    GET_PARA("getPara", GetPara.instance);
    
    private static final Map<String, APIEnum> apiByName = new HashMap<>();

    static {
        final EnumSet<APITag> tagsNotRequiringBlockchain = EnumSet.of(APITag.UTILS);
        for (APIEnum api : values()) {
            if (apiByName.put(api.getName(), api) != null) {
                AssertionError assertionError = new AssertionError("Duplicate API name: " + api.getName());
                assertionError.printStackTrace();
                throw assertionError;
            }

            final APIServlet.APIRequestHandler handler = api.getHandler();
            if (!Collections.disjoint(handler.getAPITags(), tagsNotRequiringBlockchain)
                    && handler.requireBlockchain()) {
                AssertionError assertionError = new AssertionError("API " + api.getName()
                        + " is not supposed to require blockchain");
                assertionError.printStackTrace();
                throw assertionError;
            }
        }
    }

    public static APIEnum fromName(String name) {
        return apiByName.get(name);
    }

    private final String name;
    private final APIServlet.APIRequestHandler handler;

    APIEnum(String name, APIServlet.APIRequestHandler handler) {
        this.name = name;
        this.handler = handler;
    }

    public String getName() {
        return name;
    }

    public APIServlet.APIRequestHandler getHandler() {
        return handler;
    }

    public static EnumSet<APIEnum> base64StringToEnumSet(String apiSetBase64) {
        byte[] decoded = Base64.getDecoder().decode(apiSetBase64);
        BitSet bs = BitSet.valueOf(decoded);
        EnumSet<APIEnum> result = EnumSet.noneOf(APIEnum.class);
        for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i+1)) {
            result.add(APIEnum.values()[i]);
            if (i == Integer.MAX_VALUE) {
                break; // or (i+1) would overflow
            }
        }
        return result;
    }

    public static String enumSetToBase64String(EnumSet<APIEnum> apiSet) {
        BitSet bitSet = new BitSet();
        for (APIEnum api: apiSet) {
            bitSet.set(api.ordinal());
        }
        return Base64.getEncoder().encodeToString(bitSet.toByteArray());
    }
}
