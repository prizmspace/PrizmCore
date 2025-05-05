package prizm.http;

import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import prizm.Prizm;
import prizm.PrizmException;
import prizm.Transaction;
import prizm.db.DbIterator;
import prizm.util.PrizmTree;

import javax.servlet.http.HttpServletRequest;

public class GetLoyalty extends PrizmTree.APIHierarchyRequestHandler {

    static final GetLoyalty instance = new GetLoyalty();

    private GetLoyalty() {
        super(new APITag[] {APITag.ACCOUNTS, APITag.TRANSACTIONS}, "accountParent", "accountChild");
    }

    @Override
    protected JSONStreamAware processHierarchyRequest(HttpServletRequest req) throws PrizmException {

        long child = ParameterParser.getAccountId(req, "accountChild", true);
        long parent = ParameterParser.getAccountId(req, "accountParent", true);

        if (child == 0L || parent == 0L)
            return PrizmTree.createErrorResponse("Invalid parameters!", 9799);

        long totalRecieved = 0, recievedFromParent = 0, recievedFromOthers = 0;

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
                    } else {
                        recievedFromOthers += amount;
                    }
                }
            }
        }

        JSONObject response = new JSONObject();
        response.put("total", totalRecieved);
        response.put("fromParent", recievedFromParent);
        response.put("fromOthers", recievedFromOthers);
        response.put("lastTransactionTimestamp", lastTransactionTimestamp);
        return response;
    }
}
