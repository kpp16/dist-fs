package components;

import java.util.logging.Level;
import java.util.logging.Logger;

public class TransactionManager {
    private static final Logger logger = LogManager.getLogger();

    public void executeTransaction(Transaction transaction) {
        try {
            transaction.execute();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Transaction " + transaction.getTransactionID() + " failed and rolled back.", e);
        }
    }
}
