package components;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Transaction {
    private static final AtomicLong ID_GENERATOR = new AtomicLong();
    private final long transactionID;
    private final List<Runnable> operations;
    private final List<Runnable> undoOperations;
    private static final Logger logger = LogManager.getLogger();

    public Transaction() {
        this.transactionID = ID_GENERATOR.incrementAndGet();
        this.operations = new ArrayList<>();
        this.undoOperations = new ArrayList<>();
    }

    public long getTransactionID() {
        return transactionID;
    }

    public void addOperation(Runnable operation, Runnable undoOperation) {
        operations.add(operation);
        undoOperations.add(undoOperation);
    }

    public void execute() {
        logger.log(Level.INFO, "Starting transaction: " + transactionID);
        try {
            for (Runnable operation : operations) {
                operation.run();
            }
            logger.log(Level.INFO, "Transaction " + transactionID + " completed successfully.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Transaction " + transactionID + " failed. Rolling back.", e);
            for (int i = undoOperations.size() - 1; i >= 0; i--) {
                try {
                    undoOperations.get(i).run();
                } catch (Exception rollbackException) {
                    logger.log(Level.SEVERE, "Rollback failed for transaction " + transactionID, rollbackException);
                }
            }
            throw e;
        }
    }
}