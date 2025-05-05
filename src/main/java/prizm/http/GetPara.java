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

import prizm.Account;
import prizm.Prizm;
import prizm.PrizmException;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import prizm.ParaMetrics;
// http://localhost:9576/prizm?requestType=getPara&account=4374989088324811742
public final class GetPara extends APIServlet.APIRequestHandler {

    static final GetPara instance = new GetPara();

    private GetPara() {
        super(new APITag[] {APITag.PARA}, "account");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws PrizmException {
        long accountId = ParameterParser.getAccountId(req, true);
        ParaMetrics metrics = Prizm.para().getMetrics(accountId);
        return JSONData.accountPara(metrics);
    }

}
