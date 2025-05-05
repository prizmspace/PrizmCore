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

import prizm.*;
import prizm.db.DbIterator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import static prizm.http.AAAopt._accountId;
import static prizm.http.AAAopt._error;
import static prizm.http.AAAopt._executedOnly;
import static prizm.http.AAAopt._firstIndex;
import static prizm.http.AAAopt._includeExpiredPrunable;
import static prizm.http.AAAopt._lastIndex;
import static prizm.http.AAAopt._lasttimeGetBlockchainTransactions;
import static prizm.http.AAAopt._nonPhasedOnly;
import static prizm.http.AAAopt._numberOfConfirmations;
import static prizm.http.AAAopt._phasedOnly;
import static prizm.http.AAAopt._responseGetBlockchainTransactions;
import static prizm.http.AAAopt._subtype;
import static prizm.http.AAAopt._timestamp;
import static prizm.http.AAAopt._type;
import static prizm.http.AAAopt._withMessage;
import static prizm.http.AAAopt.prinuditelnyintervaloprosa;

import static prizm.http.JSONResponses.FEATURE_NOT_AVAILABLE;

public final class GetBlockchainTransactions extends APIServlet.APIRequestHandler {

    static final GetBlockchainTransactions instance = new GetBlockchainTransactions();

    private GetBlockchainTransactions() {
        super(new APITag[] {APITag.ACCOUNTS, APITag.TRANSACTIONS}, "account", "timestamp", "type", "subtype",
                "firstIndex", "lastIndex", "numberOfConfirmations", "withMessage", "phasedOnly", "nonPhasedOnly",
                "includeExpiredPrunable", "includePhasingResult", "executedOnly");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws PrizmException {
        JSONArray transactions = new JSONArray();
        JSONObject response = new JSONObject();
        
        if(AAAopt.zanyatGBT){
            return _error("get blockcain transaction is bysi");
        }
        try{
        AAAopt.zanyatGBT=true;    
        
        long accountId = ParameterParser.getAccountId(req, true);
        if (accountId == Genesis.CREATOR_ID) {
            return FEATURE_NOT_AVAILABLE;
        }
        int timestamp = ParameterParser.getTimestamp(req);
        int numberOfConfirmations = ParameterParser.getNumberOfConfirmations(req);
        boolean withMessage = "true".equalsIgnoreCase(req.getParameter("withMessage"));
        boolean phasedOnly = "true".equalsIgnoreCase(req.getParameter("phasedOnly"));
        boolean nonPhasedOnly = "true".equalsIgnoreCase(req.getParameter("nonPhasedOnly"));
        boolean includeExpiredPrunable = "true".equalsIgnoreCase(req.getParameter("includeExpiredPrunable"));
        boolean includePhasingResult = "true".equalsIgnoreCase(req.getParameter("includePhasingResult"));
        boolean executedOnly = "true".equalsIgnoreCase(req.getParameter("executedOnly"));

        byte type;
        byte subtype;
        try {
            type = Byte.parseByte(req.getParameter("type"));
        } catch (NumberFormatException e) {
            type = -1;
        }
        try {
            subtype = Byte.parseByte(req.getParameter("subtype"));
        } catch (NumberFormatException e) {
            subtype = -1;
        }

        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        boolean ignoreRequest = false;
        
        if (Constants.SERVE_ONLY_LATEST_TRANSACTIONS) {
            if (firstIndex > 901)
                ignoreRequest = true;
            if (lastIndex > 1000)
                lastIndex = 1000;
        }
        if(!ignoreRequest && System.currentTimeMillis()-prinuditelnyintervaloprosa>_lasttimeGetBlockchainTransactions && _responseGetBlockchainTransactions!=null
                && _accountId==accountId && _numberOfConfirmations==numberOfConfirmations && _type==type && _subtype==subtype && _timestamp==timestamp
                && _withMessage==withMessage && _phasedOnly==phasedOnly &&  _nonPhasedOnly==nonPhasedOnly && _firstIndex==firstIndex
                && _lastIndex==lastIndex && _includeExpiredPrunable==includeExpiredPrunable && _executedOnly==executedOnly ){
            return _responseGetBlockchainTransactions;
        }
        
        if (!ignoreRequest) {
            try (DbIterator<? extends Transaction> iterator = Prizm.getBlockchain().getTransactions(accountId, numberOfConfirmations,
                    type, subtype, timestamp, withMessage, phasedOnly, nonPhasedOnly, firstIndex, lastIndex,
                    includeExpiredPrunable, executedOnly)) {
                while (iterator.hasNext()) {
                    Transaction transaction = iterator.next();
                    transactions.add(JSONData.transaction(transaction, includePhasingResult));
                }
            }
        }

        
        response.put("transactions", transactions);
        _responseGetBlockchainTransactions=response;
        return response;
        }finally{
            AAAopt.zanyatGBT=false;
        }

    }

}
