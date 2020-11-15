package prizm.http;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import prizm.Account;
import prizm.Prizm;
import prizm.PrizmException;
import prizm.util.Convert;
import prizm.util.PrizmTree;

import javax.servlet.http.HttpServletRequest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class GetParent extends PrizmTree.APIHierarchyRequestHandler {

    static final GetParent instance = new GetParent();
    private GetParent () {
        super (new APITag[] {APITag.ACCOUNTS}, "account");
    }
    @Override
    protected JSONStreamAware processHierarchyRequest(HttpServletRequest request) throws PrizmException {

        long account = ParameterParser.getAccountId(request, true);

        if (account == 0L || Account.getAccount(account) == null)
            return PrizmTree.createErrorResponse("Invalid \"account\"!", 9999);

        JSONObject response = new JSONObject();
        PreparedStatement statement;
        ResultSet rs;
        try {
            statement = Prizm.para().getConnection().prepareStatement("select parent_id from para where id=?");
            statement.setLong(1, account);
            rs = statement.executeQuery();
            long solvedParent = 0l;
            while (rs.next()) {
                solvedParent = rs.getLong(1);
            }
            String rsParent = Convert.rsAccount(solvedParent);
            response.put("accountRS", Convert.rsAccount(account));
            response.put("parentRS", rsParent);
            response.put("parent", Convert.parseAccountId(rsParent));
            return response;
        } catch (SQLException e) {
            throw new PrizmException.NotValidException (e.getMessage(), e.getCause());
        }
    }
}
