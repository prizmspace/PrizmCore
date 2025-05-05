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

public final class GetUnconfirmedTransactionIds extends APIServlet.APIRequestHandler {

    static final GetUnconfirmedTransactionIds instance = new GetUnconfirmedTransactionIds();

    private GetUnconfirmedTransactionIds() {
        super(new APITag[] {APITag.TRANSACTIONS, APITag.ACCOUNTS}, "account", "account", "account", "firstIndex", "lastIndex");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws ParameterException {

        Set<Long> accountIds = Convert.toSet(ParameterParser.getAccountIds(req, false));
        int firstIndex = ParameterParser.getFirstIndex(req);
        int lastIndex = ParameterParser.getLastIndex(req);

        JSONArray transactionIds = new JSONArray();
        if (accountIds.isEmpty()) {
        try (DbIterator<? extends Transaction> transactionsIterator = Prizm.getTransactionProcessor().getAllUnconfirmedTransactions(firstIndex, lastIndex)) {
            while (transactionsIterator.hasNext()) {
                Transaction transaction = transactionsIterator.next();
                    transactionIds.add(transaction.getStringId());
                }
            }
        } else {
            try (FilteringIterator<? extends Transaction> transactionsIterator = new FilteringIterator<> (
                    Prizm.getTransactionProcessor().getAllUnconfirmedTransactions(0, -1),
                    transaction -> accountIds.contains(transaction.getSenderId()) || accountIds.contains(transaction.getRecipientId()),
                    firstIndex, lastIndex)) {
                while (transactionsIterator.hasNext()) {
                    Transaction transaction = transactionsIterator.next();
                    transactionIds.add(transaction.getStringId());
                }
            }
        }

        JSONObject response = new JSONObject();
        response.put("unconfirmedTransactionIds", transactionIds);
        return response;
    }

}
