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
import prizm.Alias;
import prizm.Attachment;
import prizm.Constants;
import prizm.PrizmException;
import prizm.util.Convert;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;

import static prizm.http.JSONResponses.INCORRECT_ALIAS_LENGTH;
import static prizm.http.JSONResponses.INCORRECT_ALIAS_NAME;
import static prizm.http.JSONResponses.INCORRECT_URI_LENGTH;
import static prizm.http.JSONResponses.MISSING_ALIAS_NAME;

public final class SetAlias extends CreateTransaction {

    static final SetAlias instance = new SetAlias();

    private SetAlias() {
        super(new APITag[] {APITag.ALIASES, APITag.CREATE_TRANSACTION}, "aliasName", "aliasURI");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws PrizmException {
        return JSONResponses.FEATURE_NOT_AVAILABLE;

    }

}
