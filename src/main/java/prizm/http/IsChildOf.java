package prizm.http;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import prizm.Genesis;
import prizm.Prizm;
import prizm.PrizmException;
import prizm.util.Convert;
import prizm.util.Logger;
import prizm.util.PrizmTree;

import javax.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

public class IsChildOf extends PrizmTree.APIHierarchyRequestHandler {

    static final IsChildOf instance = new IsChildOf();

    private IsChildOf() {
        super(new APITag[] {APITag.ACCOUNTS}, "childRS", "parentRS");
    }

    protected JSONStreamAware processHierarchyRequest(HttpServletRequest req) throws PrizmException {

        long child = ParameterParser.getAccountId(req, "childRS", true);
        long parent = ParameterParser.getAccountId(req, "parentRS", true);

        JSONObject response = new JSONObject();

        if (child == 0L || parent == 0L)
            return PrizmTree.createErrorResponse("Invalid parameters!", 9950);

        if (child == parent) {
            response.put("errorDescription", "Child equals parent");
            response.put("errorCode", "9502");
            return response;
        }

        final Connection conn = Prizm.para().getConnection();
        if (conn == null) {
            final JSONObject err = new JSONObject();
            err.put("errorDescription","Failed to retrieve database connection!");
            err.put("errorCode","9506");
            return err;
        }

        long solvedParent = child;
        try(PreparedStatement statement = conn.prepareStatement("select parent_id from para where id=?")){
            statement.setLong(1, child);
            try(ResultSet rs = statement.executeQuery()){
                while (rs.next()) {
                    solvedParent = rs.getLong(1);
                }
            }} catch (SQLException ex) {
            java.util.logging.Logger.getLogger(IsChildOf.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (solvedParent == child) {
            response.put("errorDescription", "Account " + Convert.rsAccount(child) + " is not part of any hierarchy");
            response.put("errorCode", "9503");
            return response;
        }
        int i = 0;
        boolean haveParent = false;
        while (solvedParent != parent && solvedParent != Genesis.CREATOR_ID) {
            try(PreparedStatement statement = conn.prepareStatement("select parent_id from para where id=?")){
                statement.setLong(1, solvedParent);
                try(ResultSet rs = statement.executeQuery()){
                    haveParent = false;
                    while (rs.next()) {
                        solvedParent = rs.getLong(1);
                        haveParent = true;
                    }
                    if (i == 88) {
                        solvedParent = Genesis.CREATOR_ID;
                        break;
                    }
                }} catch (SQLException ex) {
                java.util.logging.Logger.getLogger(IsChildOf.class.getName()).log(Level.SEVERE, null, ex);
            }
            if (!haveParent) {
                response.put("errorDescription", "Error solving top-level parent account - it is impossible situation, please report this");
                response.put("errorCode", "9504");
                return response;
            }
            i++;
        }
        if (solvedParent == parent) {
            response.put("childRS", req.getParameter("childRS"));
            response.put("isChild", true);
            return response;
        }
        if (solvedParent == Genesis.CREATOR_ID) {
            response.put("childRS", req.getParameter("childRS"));
            response.put("isChild", false);
            return response;
        }
        response.put("errorDescription", "Unknown error");
        response.put("errorCode", "9505");
        return response;
                
       
    }
}
