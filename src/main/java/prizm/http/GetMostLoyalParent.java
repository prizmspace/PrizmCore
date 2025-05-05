package prizm.http;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import prizm.Account;
import prizm.Prizm;
import prizm.PrizmException;
import prizm.Transaction;
import prizm.db.DbIterator;
import prizm.util.Convert;
import prizm.util.PrizmTree;

import javax.servlet.http.HttpServletRequest;

public class GetMostLoyalParent extends PrizmTree.APIHierarchyRequestHandler {

    static final GetMostLoyalParent instance = new GetMostLoyalParent();

    private GetMostLoyalParent() {
        super(new APITag[] {APITag.PARA}, "accountChild");
    }

    @Override
    protected JSONStreamAware processHierarchyRequest(HttpServletRequest req) throws PrizmException {

        long accountId = ParameterParser.getAccountId(req, "accountChild", true);
        if (accountId == 0L)
            return PrizmTree.createErrorResponse("Invalid \"accountChild\"!", 9899);
        Account account = Account.getAccount(accountId);
        if (account == null)
            throw new PrizmException.NotValidException("Invalid account");
        PrizmTree.AccountLoyaltyContainer container = PrizmTree.getMostLoyalParentFaster (account);
        if (container == null || container.account == null)
            throw new PrizmException.NotValidException ("Loyal parent not found (2)");
        JSONObject response = new JSONObject();
        response.put("loyalParent", Convert.rsAccount(container.account.getId()));
        response.put("loyalty", container.loyalty);
        return response;
    }
}
