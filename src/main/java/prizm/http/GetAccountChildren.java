package prizm.http;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import prizm.Account;
import prizm.Db;
import prizm.Prizm;
import prizm.PrizmException;
import prizm.util.Convert;
import prizm.util.Logger;
import prizm.util.PrizmTree;
import javax.servlet.http.HttpServletRequest;
import java.sql.*;
import java.util.*;
import static prizm.util.PrizmTree.getDirectChildrenOf;
import static prizm.util.PrizmTree.getParentOf;
import static prizm.util.PrizmTree.getRootAccountMinimal;
import static prizm.util.PrizmTree.AccountMinimal;

public class GetAccountChildren extends PrizmTree.APIHierarchyRequestHandler {

    static final GetAccountChildren instance = new GetAccountChildren ();

    private GetAccountChildren() {
        super(new APITag[] {APITag.ACCOUNTS}, "account", "firstIndex");
    }

    public static final int         MAX_DEPTH_PER_REQUEST =                 88;

    protected JSONStreamAware processHierarchyRequest(HttpServletRequest req) throws PrizmException {
        if (Prizm.para().getConnection() == null) {
            JSONObject response = new JSONObject();
            response.put("errorDescription", "GetAccountChildren API failed to connect to the database");
            response.put("errorCode", "123");
            return response;
        }

        final long accountID = ParameterParser.getAccountId(req, true);
        if (accountID == 0L) {
            return PrizmTree.createErrorResponse("Invalid account!", 9699);
        }

        final int startIndex = ParameterParser.getFirstIndex(req);

        final Account accountObject = Account.getAccount(accountID);

        if (accountObject == null)
            return PrizmTree.createErrorResponse("Account "+accountID+" not found", 9601);

        final AccountMinimal parent = getParentOf(accountID);
        final AccountMinimal account = getRootAccountMinimal(accountID);

        if (parent == null || account == null)
            return PrizmTree.createErrorResponse("Impossible to solve hierarchy for this account", 9698);

        List<AccountMinimal> children;

        try {
            children = getDirectChildrenOf(accountID, 1, 2, true, startIndex, true);
        } catch (SQLException e) {
            Logger.logErrorMessage(e.getMessage(), e);
            return PrizmTree.createErrorResponse("Failed to process request", 9699);
        }

        JSONObject response = new JSONObject();
        JSONArray childrenJson = new JSONArray();

        for (AccountMinimal a : children) {
            childrenJson.add(a.toJSONObject());
        }

        response.put("children", childrenJson);

        return response;
    }

}

