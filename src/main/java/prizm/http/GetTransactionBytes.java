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
import prizm.util.Convert;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static prizm.http.JSONResponses.INCORRECT_TRANSACTION;
import static prizm.http.JSONResponses.MISSING_TRANSACTION;
import static prizm.http.JSONResponses.UNKNOWN_TRANSACTION;

public final class GetTransactionBytes extends APIServlet.APIRequestHandler {

    static final GetTransactionBytes instance = new GetTransactionBytes();

    private GetTransactionBytes() {
        super(new APITag[] {APITag.TRANSACTIONS}, "transaction");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        String transactionValue = req.getParameter("transaction");
        if (transactionValue == null) {
            return MISSING_TRANSACTION;
        }

        long transactionId;
        Transaction transaction;
        try {
            transactionId = Convert.parseUnsignedLong(transactionValue);
        } catch (RuntimeException e) {
            return INCORRECT_TRANSACTION;
        }

        transaction = Prizm.getBlockchain().getTransaction(transactionId);
        JSONObject response = new JSONObject();
        if (transaction == null) {
            transaction = Prizm.getTransactionProcessor().getUnconfirmedTransaction(transactionId);
            if (transaction == null) {
                return UNKNOWN_TRANSACTION;
            }
        } else {
            response.put("confirmations", Prizm.getBlockchain().getHeight() - transaction.getHeight());
        }
        response.put("transactionBytes", Convert.toHexString(transaction.getBytes()));
        response.put("unsignedTransactionBytes", Convert.toHexString(transaction.getUnsignedBytes()));
        JSONData.putPrunableAttachment(response, transaction);
        return response;

    }

}
