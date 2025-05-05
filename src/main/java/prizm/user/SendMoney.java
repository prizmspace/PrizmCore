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

package prizm.user;

import prizm.Account;
import prizm.Attachment;
import prizm.Constants;
import prizm.Prizm;
import prizm.PrizmException;
import prizm.Transaction;
import prizm.util.Convert;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

import static prizm.user.JSONResponses.NOTIFY_OF_ACCEPTED_TRANSACTION;

public final class SendMoney extends UserServlet.UserRequestHandler {

    static final SendMoney instance = new SendMoney();

    private SendMoney() {}

    @Override
    JSONStreamAware processRequest(HttpServletRequest req, User user) throws PrizmException.ValidationException, IOException {
        if (user.getSecretPhrase() == null) {
            return null;
        }

        String recipientValue = req.getParameter("recipient");
        String amountValue = req.getParameter("amountPRIZM");
        String feeValue = req.getParameter("feePRIZM");
        String deadlineValue = req.getParameter("deadline");
        String secretPhrase = req.getParameter("secretPhrase");

        long recipient;
        long amountNQT = 0;
        long feeNQT = 0;
        short deadline = 0;

        try {

            recipient = Convert.parseUnsignedLong(recipientValue);
            if (recipient == 0) throw new IllegalArgumentException("invalid recipient");
            amountNQT = Convert.parsePRIZM(amountValue.trim());
//            feeNQT = Convert.parsePRIZM(feeValue.trim());
        feeNQT=Prizm.para().getFixedFee(amountNQT);
            deadline = (short)(Double.parseDouble(deadlineValue) * 60);

        } catch (RuntimeException e) {

            JSONObject response = new JSONObject();
            response.put("response", "notifyOfIncorrectTransaction");
            response.put("message", "One of the fields is filled incorrectly!");
            response.put("recipient", recipientValue);
            response.put("amountPRIZM", amountValue);
            response.put("feePRIZM", feeValue);
            response.put("deadline", deadlineValue);

            return response;
        }

        if (! user.getSecretPhrase().equals(secretPhrase)) {

            JSONObject response = new JSONObject();
            response.put("response", "notifyOfIncorrectTransaction");
            response.put("message", "Wrong secret phrase!");
            response.put("recipient", recipientValue);
            response.put("amountPRIZM", amountValue);
            response.put("feePRIZM", feeValue);
            response.put("deadline", deadlineValue);

            return response;

        } else if (amountNQT <= 0 || amountNQT > Constants.MAX_BALANCE_NQT) {

            JSONObject response = new JSONObject();
            response.put("response", "notifyOfIncorrectTransaction");
            response.put("message", "\"Amount\" must be greater than 0!");
            response.put("recipient", recipientValue);
            response.put("amountPRIZM", amountValue);
            response.put("feePRIZM", feeValue);
            response.put("deadline", deadlineValue);

            return response;

        } else if (feeNQT < Constants.ONE_PRIZM || feeNQT > Constants.MAX_BALANCE_NQT) {

            JSONObject response = new JSONObject();
            response.put("response", "notifyOfIncorrectTransaction");
            response.put("message", "\"Fee\" must be at least 0.05 PRIZM!");
            response.put("recipient", recipientValue);
            response.put("amountPRIZM", amountValue);
            response.put("feePRIZM", feeValue);
            response.put("deadline", deadlineValue);

            return response;

        } else if (deadline < 1 || deadline > 1440) {

            JSONObject response = new JSONObject();
            response.put("response", "notifyOfIncorrectTransaction");
            response.put("message", "\"Deadline\" must be greater or equal to 1 minute and less than 24 hours!");
            response.put("recipient", recipientValue);
            response.put("amountPRIZM", amountValue);
            response.put("feePRIZM", feeValue);
            response.put("deadline", deadlineValue);

            return response;

        }

        Account account = Account.getAccount(user.getPublicKey());
        if (account == null || Math.addExact(amountNQT, feeNQT) > account.getUnconfirmedBalanceNQT()) {

            JSONObject response = new JSONObject();
            response.put("response", "notifyOfIncorrectTransaction");
            response.put("message", "Not enough funds!");
            response.put("recipient", recipientValue);
            response.put("amountPRIZM", amountValue);
            response.put("feePRIZM", feeValue);
            response.put("deadline", deadlineValue);

            return response;

        } else {

            final Transaction transaction = Prizm.newTransactionBuilder(user.getPublicKey(),
                    amountNQT, feeNQT, deadline, Attachment.ORDINARY_PAYMENT).recipientId(recipient).build(secretPhrase);

            Prizm.getTransactionProcessor().broadcast(transaction);

            return NOTIFY_OF_ACCEPTED_TRANSACTION;

        }
    }

    @Override
    boolean requirePost() {
        return true;
    }

}
