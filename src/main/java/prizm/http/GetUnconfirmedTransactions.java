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

import prizm.Prizm;
import prizm.Transaction;
import prizm.db.DbIterator;
import prizm.db.FilteringIterator;
import prizm.util.Convert;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Set;
import static prizm.http.AAAopt._error;
import static prizm.http.AAAopt._responseGUT;
import static prizm.http.AAAopt._zanyatGUT;

public final class GetUnconfirmedTransactions extends APIServlet.APIRequestHandler {

    static final GetUnconfirmedTransactions instance = new GetUnconfirmedTransactions();

    private GetUnconfirmedTransactions() {
        super(new APITag[] {APITag.TRANSACTIONS, APITag.ACCOUNTS}, "account", "account", "account", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        JSONObject response = new JSONObject();
        JSONArray transactions = new JSONArray();
        if(_zanyatGUT){
            if(_responseGUT!=null){
                return _responseGUT;
            }else{
                return _error("get unconfirmed TX is bisy");    
            }
        }
        try{
        _zanyatGUT=true;    
        
        Set<Long> accountIds = Convert.toSet(ParameterParser.getAccountIds(req, false));
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        
        if (accountIds.isEmpty()) {
            try (DbIterator<? extends Transaction> transactionsIterator = Prizm.getTransactionProcessor().getAllUnconfirmedTransactions(firstIndex, lastIndex)) {
            while (transactionsIterator.hasNext()) {
                Transaction transaction = transactionsIterator.next();
                    transactions.add(JSONData.unconfirmedTransaction(transaction));
                }
            }
        } else {
            try (FilteringIterator<? extends Transaction> transactionsIterator = new FilteringIterator<> (
                    Prizm.getTransactionProcessor().getAllUnconfirmedTransactions(0, -1),
                    transaction -> accountIds.contains(transaction.getSenderId()) || accountIds.contains(transaction.getRecipientId()),
                    firstIndex, lastIndex)) {
                while (transactionsIterator.hasNext()) {
                    Transaction transaction = transactionsIterator.next();
                    transactions.add(JSONData.unconfirmedTransaction(transaction));
                }
            }
        }
        response.put("unconfirmedTransactions", transactions);
        return response;
        }finally{
             _zanyatGUT=false;
        }
    }

}
