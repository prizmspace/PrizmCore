package prizm.util;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import prizm.*;
import prizm.db.DbIterator;
import prizm.http.APIServlet;
import prizm.http.APITag;
import prizm.http.ParameterParser;

import javax.servlet.http.HttpServletRequest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static prizm.http.JSONResponses.ERROR_NOT_ALLOWED;

public class PrizmTree {

    public static final boolean     ALLOW_API_HIERARCHY_WITHOUT_PASSWORD =  Prizm.getBooleanProperty("prizm.allowAPIHierarchyWithoutPassword", false);
    public static final boolean     ALLOW_API_HIERARCHY_ONLY_LOCALHOST   =  Prizm.getBooleanProperty("prizm.allowAPIHierarchyOnlyLocalhost", true);

    public static double getLoyalty (long child, long parent) {
        long totalRecieved = 0, recievedFromParent = 0;
        int lastTransactionTimestamp = -1;
        try (DbIterator<? extends Transaction> iterator = Prizm.getBlockchain().getTransactions(child, (byte) -1, (byte) -1, 0, false)) {
            while (iterator.hasNext()) {
                Transaction transaction = iterator.next();
                long amount = transaction.getAmountNQT();
                if (transaction.getRecipientId()==child) {
                    totalRecieved += amount;
                    if (transaction.getSenderId()==parent) {
                        recievedFromParent += amount;
                        int timestamp = transaction.getTimestamp();
                        if (timestamp > lastTransactionTimestamp)
                            lastTransactionTimestamp = timestamp;
                    }
                }
            }
        }
        if (totalRecieved == 0)
            return 0d;
        double loyalty  = ((double) recievedFromParent / (double) totalRecieved) * 100d;
        loyalty = Math.round(loyalty * 100d) / 100d;
        return loyalty;
    }

    public static Account getParent (Account a) {
        if (a == null)
            return null;
        long account = a.getId();
        try {
            try (PreparedStatement statement = Prizm.para().getConnection().prepareStatement("select parent_id from para where id=?")) {
                statement.setLong(1, account);
                try (ResultSet rs = statement.executeQuery()) {
                    long solvedParent = 0l;
                    while (rs.next()) {
                        solvedParent = rs.getLong(1);
                    }
                    Account parent = Account.getAccount(solvedParent);
                    if (parent != null && solvedParent != 0l)
                        return parent;
                }
            }

        } catch (SQLException e) {
            Logger.logErrorMessage("Failed to get parent of " + a.getId(), e);
            return null;
        }
        return null;
    }

    public static long getParentID (long account) {
        try {
            try (PreparedStatement statement = Prizm.para().getConnection().prepareStatement("select parent_id from para where id=?")) {
                statement.setLong(1, account);
                try (ResultSet rs = statement.executeQuery()) {
                    long solvedParent = 0L;
                    while (rs.next()) {
                        solvedParent = rs.getLong(1);
                    }
                    return solvedParent;
                }
            }
        } catch (SQLException e) {
            Logger.logErrorMessage("Failed to get parent of " + account, e);
        }
        return 0L;
    }

    public static AccountMinimal getParentOf (long accountId) {

        if (accountId == Genesis.CREATOR_ID) {
            try {
                return new AccountMinimal(0L, 0, -1, 0L, 0L);
            } catch (Exception e) {
                Logger.logErrorMessage("Failed to get parent of " + accountId, e);
                return null;
            }
        }

        final long parentID = getParentID(accountId);
        if (parentID == 0L)
            return null;
        try {
            try (PreparedStatement statement = Prizm.para().getConnection().prepareStatement("select balance,amount from para where id=?")) {
                statement.setLong(1, parentID);
                try (ResultSet rs = statement.executeQuery()) {
                    AccountMinimal parent = null;
                    while (rs.next()) {
                        parent = new AccountMinimal(parentID, 0, -1, rs.getLong(1), rs.getLong(2));
                    }
                    return parent;
                }
            }
        } catch (SQLException e) {
            Logger.logErrorMessage("Failed to get parent of " + accountId, e);
        } catch (Exception e) {
            Logger.logErrorMessage(e.getMessage(), e);
        }
        return null;
    }

    public static AccountMinimal getRootAccountMinimal (long accountId) {
        if (accountId == 0L)
            return null;
        try {
            try (PreparedStatement statement = Prizm.para().getConnection().prepareStatement("select balance,amount from para where id=?")) {
                statement.setLong(1, accountId);
                try (ResultSet rs = statement.executeQuery()) {
                    AccountMinimal accountMinimal = null;
                    while (rs.next()) {
                        accountMinimal = new AccountMinimal(accountId, 1, 0, rs.getLong(1), rs.getLong(2));
                    }
                    return accountMinimal;
                }
            }
        } catch (SQLException e) {
            Logger.logErrorMessage("Failed to get AccountMinimal: " + accountId, e);
        } catch (Exception e) {
            Logger.logErrorMessage(e.getMessage(), e);
        }
        return null;
    }

    public static List<AccountMinimal> getDirectChildrenOf (long accountId, int internalParentID, int internalIDStart, boolean limit, int startIndex, boolean orderByAmount) throws SQLException {
        List<AccountMinimal> children = new ArrayList<>();
        try {
            try (PreparedStatement statement = Prizm.para().getConnection().prepareStatement("select id,balance,amount from para where parent_id=?"  + (orderByAmount?" order by amount desc":"") + (limit?" limit 100"+(startIndex>0?" offset ?":""):"") )) {
                statement.setLong(1, accountId);
                if (limit && startIndex > 0)
                    statement.setInt(2, startIndex);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        final long id = rs.getLong(1);
                        final AccountMinimal child;
                        try {
                            child = new AccountMinimal(rs.getLong(1), internalIDStart++, internalParentID, rs.getLong(2), rs.getLong(3));
                            if (child.reedSolomonMinimal().equalsIgnoreCase("PPPP-PPPP-PPPP-PPPPP"))
                                continue;
                        } catch (Exception e) {
                            Logger.logErrorMessage(e.getMessage(), e);
                            continue;
                        }
                        children.add(child);
                    }
                }
            }
        } catch (SQLException e) {
            Logger.logErrorMessage("Failed to get children of " + accountId, e);
        }
        return children;
    }

    public static int getChildCountOf (long accountId) {
        try {
            try (PreparedStatement statement = Prizm.para().getConnection().prepareStatement("select count(*) from para where parent_id=?")) {
                statement.setLong(1, accountId);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        final int count = rs.getInt(1);
                        return count;
                    }
                }
            }
        } catch (SQLException e) {
            Logger.logErrorMessage("Failed to get childCount of " + accountId, e);
        }
        return 0;
    }

    // Format accounts list in CSV-style to response in JSON array
    // Such move decreases response size and network usage by 60%
    // Due to limited depth the memory consumption is not valuable
    public static class AccountMinimal {
        static final String com = ",";
        public final String name, description;
        public final long id, balance, amount, forged;
        public final int internalID, parentInternalID;
        public AccountMinimal (long id, int internalID, int parentInternalID, long balance, long amount) throws Exception {
            Account a = Account.getAccount(id);
            if (a == null && id != 0L)
                throw new Exception("Invalid account: " + id);
            this.id = id;
            this.internalID = internalID;
            this.parentInternalID = parentInternalID;
            this.balance = balance;
            this.amount = amount;
            this.forged = Prizm.getBlockchain().getBlockCount(id);
            if (id != 0L) {
                Account.AccountInfo info = a.getAccountInfo();
                if (info == null) {
                    name = "";
                    description = "";
                } else {
                    name = info.getName();
                    if (info.getDescription() == null || info.getDescription().equals("null"))
                        description = "";
                    else
                        description = info.getDescription();
                }
            } else {
                name = "GRANDFATHER";
                description = "GRANDFATHER";
            }
        }
        @Override
        public String toString () { // Replace comma inside name and description to save CSV data structure
            return internalID + com + parentInternalID + com + reedSolomonMinimal() + com + balance + com + amount + com + forged + com + name.replaceAll(",", "%2C") + com + description.replaceAll(",", "%2C");
        }
        public JSONObject toJSONObject () {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("accountRS", Convert.rsAccount(id));
            jsonObject.put("balanceNQT", balance);
            jsonObject.put("amountNQT", amount);
            jsonObject.put("name", name.replaceAll(",", "%2C"));
            jsonObject.put("description", description.replaceAll(",", "%2C"));
            jsonObject.put("childCount", getChildCountOf(id));
            jsonObject.put("forging", forged>0);
            return jsonObject;
        }
        public String reedSolomonMinimal () {
            return Convert.rsAccount(id).substring(6);
        }

    }

    public static List<Account> getRisingBranch (Account account) {
        List<Account> branch = new ArrayList<Account>();
        if (account == null)
            return branch;
        Account parent = getParent(account);
        while (parent != null && parent.getId() != Genesis.CREATOR_ID) {
            branch.add(parent);
            parent = getParent(parent);
        }
        return branch;
    }

    public static class AccountLoyaltyContainer {
        public AccountLoyaltyContainer (Account a, double l) {
            account = a;
            loyalty = l;
        }
        public Account account;
        public double loyalty;
    }

    // Legacy method, left here for history

//    public static AccountLoyaltyContainer getMostLoyalParent (Account account) {
//        List<Account> root = getRisingBranch(account);
//        if (root.size() == 0)
//            return null;
//        double maxLoyalty = getLoyalty(account.getId(), root.get(0).getId());
//        Account loyalParent = root.get(0);
//        for (int i = 1; i < root.size(); i++) {
//            double loyalty = getLoyalty(account.getId(), root.get(i).getId());
//            if (loyalty > maxLoyalty) {
//                maxLoyalty = loyalty;
//                loyalParent = root.get(i);
//            }
//        }
//        return new AccountLoyaltyContainer(loyalParent, maxLoyalty);
//    }

    // More memory consuming, but much more faster
    public static AccountLoyaltyContainer getMostLoyalParentFaster (Account account) {
        List<Account> root = getRisingBranch(account);
        if (root.size() == 0)
            return null;
        HashMap<Long, Long> map = new HashMap<Long,Long>();

        long totalRecieved = 0l;

        try (DbIterator<? extends Transaction> iterator = Prizm.getBlockchain().getTransactions(account.getId(), (byte) -1, (byte) -1, 0, false)) {
            while (iterator.hasNext()) {
                Transaction transaction = iterator.next();
                if (transaction.getRecipientId() == account.getId() && transaction.getSenderId() != Genesis.CREATOR_ID) {
                    totalRecieved += transaction.getAmountNQT();
                    map.put(
                            transaction.getSenderId(),
                            (map.containsKey(transaction.getSenderId())? map.get(transaction.getSenderId()) : 0l)
                                    + transaction.getAmountNQT());

                }
            }
        }

        Long maxAmount = 0L;
        Account loyalParent = null;

        for (int i = 0; i < root.size(); i++) {
            if (map.containsKey(root.get(i).getId())) {
                Long amount = map.get(root.get(i).getId());
                if (amount > maxAmount) {
                    loyalParent = root.get(i);
                    maxAmount = amount;
                }
            }
        }

        if (loyalParent == null)
            return null;
        double loyalty = ((double) maxAmount / (double) totalRecieved) * 100d;

        return new AccountLoyaltyContainer(loyalParent, Math.round(loyalty * 100d) / 100d);
    }

    public static JSONObject createErrorResponse (String errorDescription, int errorCode) {
        JSONObject obj = new JSONObject();
        obj.put("errorDescription", errorDescription);
        obj.put("errorCode", errorCode);
        return  obj;
    }

    public static abstract class APIHierarchyRequestHandler extends APIServlet.APIRequestHandler {
        protected APIHierarchyRequestHandler(APITag[] tags, String...parameters) {
            super(tags, parameters);
        }
        @Override
        public JSONStreamAware processRequest (HttpServletRequest req) throws PrizmException {
            if (ALLOW_API_HIERARCHY_ONLY_LOCALHOST) {
                final String host = req.getRemoteHost();
                final boolean isLocalhost = host.equals("127.0.0.1") || host.equals("localhost") || host.equals("[0:0:0:0:0:0:0:1]") || host.equals("0:0:0:0:0:0:0:1");
                if (!isLocalhost)
                    return ERROR_NOT_ALLOWED;
            }
            return processHierarchyRequest(req);
        }
        protected abstract JSONStreamAware processHierarchyRequest(HttpServletRequest req) throws PrizmException;
        @Override
        protected final boolean requirePassword() {
            return !PrizmTree.ALLOW_API_HIERARCHY_WITHOUT_PASSWORD;
        }
    }

}
