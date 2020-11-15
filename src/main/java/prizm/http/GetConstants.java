/******************************************************************************
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
 ******************************************************************************/

package prizm.http;

import prizm.Constants;
import prizm.Genesis;
import prizm.HoldingType;
import prizm.TransactionType;
import prizm.crypto.HashFunction;
import prizm.peer.Peer;
import prizm.util.JSON;
import prizm.util.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import prizm.util.PrizmTree;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Map;

public final class GetConstants extends APIServlet.APIRequestHandler {

    static final GetConstants instance = new GetConstants();

    private static final class Holder {

        private static final JSONStreamAware CONSTANTS;

        static {
            try {
                JSONObject response = new JSONObject();
                response.put("genesisBlockId", Long.toUnsignedString(Genesis.GENESIS_BLOCK_ID));
                response.put("genesisAccountId", Long.toUnsignedString(Genesis.CREATOR_ID));
                response.put("epochBeginning", Constants.EPOCH_BEGINNING);
                response.put("maxBlockPayloadLength", Constants.MAX_PAYLOAD_LENGTH);
                response.put("maxArbitraryMessageLength", Constants.MAX_ARBITRARY_MESSAGE_LENGTH);
                response.put("maxPrunableMessageLength", Constants.MAX_PRUNABLE_MESSAGE_LENGTH);

                JSONObject transactionJSON = new JSONObject();
                JSONObject transactionSubTypesJSON = new JSONObject();
                outer:
                for (int type = 0; ; type++) {
                    JSONObject typeJSON = new JSONObject();
                    JSONObject subtypesJSON = new JSONObject();
                    for (int subtype = 0; ; subtype++) {
                        TransactionType transactionType = TransactionType.findTransactionType((byte) type, (byte) subtype);
                        if (transactionType == null) {
                            if (subtype == 0) {
                                break outer;
                            } else {
                                break;
                            }
                        }
                        JSONObject subtypeJSON = new JSONObject();
                        subtypeJSON.put("name", transactionType.getName());
                        subtypeJSON.put("canHaveRecipient", transactionType.canHaveRecipient());
                        subtypeJSON.put("mustHaveRecipient", transactionType.mustHaveRecipient());
                        subtypeJSON.put("type", type);
                        subtypeJSON.put("subtype", subtype);
                        subtypesJSON.put(subtype, subtypeJSON);
                        transactionSubTypesJSON.put(transactionType.getName(), subtypeJSON);
                    }
                    typeJSON.put("subtypes", subtypesJSON);
                    transactionJSON.put(type, typeJSON);
                }
                response.put("transactionTypes", transactionJSON);
                response.put("transactionSubTypes", transactionSubTypesJSON);

                JSONObject hashFunctions = new JSONObject();
                for (HashFunction hashFunction : HashFunction.values()) {
                    hashFunctions.put(hashFunction.toString(), hashFunction.getId());
                }
                response.put("hashAlgorithms", hashFunctions);

                JSONObject peerStates = new JSONObject();
                for (Peer.State peerState : Peer.State.values()) {
                    peerStates.put(peerState.toString(), peerState.ordinal());
                }
                response.put("peerStates", peerStates);

                JSONObject requestTypes = new JSONObject();
                for (Map.Entry<String, APIServlet.APIRequestHandler> handlerEntry : APIServlet.apiRequestHandlers.entrySet()) {
                    JSONObject handlerJSON = JSONData.apiRequestHandler(handlerEntry.getValue());
                    handlerJSON.put("enabled", true);
                    requestTypes.put(handlerEntry.getKey(), handlerJSON);
                }
                for (Map.Entry<String, APIServlet.APIRequestHandler> handlerEntry : APIServlet.disabledRequestHandlers.entrySet()) {
                    JSONObject handlerJSON = JSONData.apiRequestHandler(handlerEntry.getValue());
                    handlerJSON.put("enabled", false);
                    requestTypes.put(handlerEntry.getKey(), handlerJSON);
                }
                response.put("requestTypes", requestTypes);

                JSONObject holdingTypes = new JSONObject();
                for (HoldingType holdingType : HoldingType.values()) {
                    holdingTypes.put(holdingType.toString(), holdingType.getCode());
                }
                response.put("holdingTypes", holdingTypes);

                JSONObject apiTags = new JSONObject();
                for (APITag apiTag : APITag.values()) {
                    JSONObject tagJSON = new JSONObject();
                    tagJSON.put("name", apiTag.getDisplayName());
                    tagJSON.put("enabled", !API.disabledAPITags.contains(apiTag));
                    apiTags.put(apiTag.name(), tagJSON);
                }
                response.put("apiTags", apiTags);

                JSONArray disabledAPIs = new JSONArray();
                Collections.addAll(disabledAPIs, API.disabledAPIs);
                response.put("disabledAPIs", disabledAPIs);

                JSONArray disabledAPITags = new JSONArray();
                API.disabledAPITags.forEach(apiTag -> disabledAPITags.add(apiTag.getDisplayName()));
                response.put("disabledAPITags", disabledAPITags);

                JSONArray notForwardedRequests = new JSONArray();
                notForwardedRequests.addAll(APIProxy.NOT_FORWARDED_REQUESTS);
                response.put("proxyNotForwardedRequests", notForwardedRequests);

                CONSTANTS = JSON.prepare(response);
            } catch (Exception e) {
                Logger.logErrorMessage(e.toString(), e);
                throw e;
            }
        }
    }

    private GetConstants() {
        super(new APITag[] {APITag.INFO});
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        return Holder.CONSTANTS;
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

    @Override
    protected boolean requireBlockchain() {
        return false;
    }

    public static JSONStreamAware getConstants() {
        return Holder.CONSTANTS;
    }
}
