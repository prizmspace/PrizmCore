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
package prizm.http;

import prizm.Account;
import prizm.PrizmException;
import prizm.util.Convert;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import static prizm.http.AAAopt._mapACC;
import static prizm.http.AAAopt._maxHMsizeAcc;
import static prizm.http.AAAopt._zanatsetACC;

public final class GetAccount extends APIServlet.APIRequestHandler {

    static final GetAccount instance = new GetAccount();

    private GetAccount() {
        super(new APITag[]{APITag.ACCOUNTS}, "account", "includeEffectiveBalance");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws PrizmException {

        String paramValue = Convert.emptyToNull(req.getParameter("account"));
        if (paramValue == null) {
            return JSONResponses.ERROR_INCORRECT_REQUEST;
        }
        long accId = -1L;
        try {
            accId = Long.parseLong(paramValue);
        } catch (Exception e) {
        }
        long accRSId = -1L;
        try {
            accRSId = prizm.util.Convert.parseAccountId(paramValue);
        } catch (Exception e) {
        }

        if (accId > 0 && accRSId > 0 && accId != accRSId) {
            return JSONResponses.ERROR_INCORRECT_REQUEST;
        }
        if (accId < 0) {
            accId = accRSId;
        }
        
        if (_zanatsetACC.contains(accId) ) {
            if(_mapACC.containsKey(accId)){
                return _mapACC.get(accId);
            }
            return JSONResponses.ERROR_NOT_ALLOWED;
        }
        try {
            _zanatsetACC.add(accId);

            boolean includeEffectiveBalance = "true".equalsIgnoreCase(req.getParameter("includeEffectiveBalance"));

            Account account = ParameterParser.getAccount(req);

            JSONObject response = JSONData.accountBalance(account, includeEffectiveBalance);
            JSONData.putAccount(response, "account", account.getId());

            byte[] publicKey = Account.getPublicKey(account.getId());
            if (publicKey != null) {
                response.put("publicKey", Convert.toHexString(publicKey));
            }
            Account.AccountInfo accountInfo = account.getAccountInfo();
            if (accountInfo != null) {
                response.put("name", Convert.nullToEmpty(accountInfo.getName()));
                response.put("description", Convert.nullToEmpty(accountInfo.getDescription()));
            }
            _mapACC.put(accId, response);
            if (_mapACC.size() > _maxHMsizeAcc) {
                _mapACC.remove(_mapACC.firstEntry().getKey());
            }
            return response;
        } finally {
            _zanatsetACC.remove(accId);
        }

    }


}
