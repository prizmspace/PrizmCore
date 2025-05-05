/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tooforcels | Templates
 * and open the template in the editor.
 */
package prizm;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import prizm.util.Logger;
import prizm.util.BoostMap;

/**
 * @author zoi
 * select para.balance, para.id from para left join block on block.creator_id = para.id group by para.id limit 10;
 * 
 * select para.balance from para,block where block.creator_id = para.id and block.height>=400000 and block.height<500000 group by para.id;
 * 
 */
public class ParaEngine implements ParaMining {

    public static final long MAXIMUM_PARAMINING_AMOUNT = -600000000000l;

    public static final int CACHE_SIZE = 820;
    public static final int CACHE_DEEP = 1450;

    public static final String LAST_1440_BLOCK = "last_1440_block", FAST_ROLLBACK_ENABLED_HEIGHT = "fast_rollback_enabled_height", PARABASE_FAST_ROLLBACK_UPDATE_HEIGHT = "parabase_fast_rollback_update_height", MIN_FAST_ROLLBACK_HEIGHT = "min_fast_rollback_height", HOLD_UPDATE_HEIGHT = "hold_update_height", ZEROBLOCK_FIXED = "zeroblock_fixed", HOLD_INTEGRITY_VALIDATED = "hold_validated";

    public static class ParaPair {

        private ParaMetrics metricsSender;
        private ParaMetrics metricsReceiver;

        public ParaMetrics getMetricsSender() {
            return metricsSender;
        }

        public void setMetricsSender(ParaMetrics metricsSender) {
            this.metricsSender = metricsSender;
        }

        public ParaMetrics getMetricsReceiver() {
            return metricsReceiver;
        }

        public void setMetricsReceiver(ParaMetrics metricsReceiver) {
            this.metricsReceiver = metricsReceiver;
        }
    }

    public static final String ERROR_DATABASE_CLOSED = "Database closed!";
    public static final String ERROR_CANT_COMMIT = "Can't commit transaction!";
    public static final String ERROR_CANT_INITIALIZE = "Can't initialize database!";
    public static final String ERROR_DRIVER_NOT_FOUND = "H2 Driver not found!";
    public static final String ERROR_CANT_CONNECT = "Can't connect to database!";
    public static final String ERROR_ALREADY = "Key already exists!";
    public static final String ERROR_ERROR = "Unknown core error!";
    public static final String ERROR_INVALID_TRANSACTION = "Invalid transaction!";
    public static final String ERROR_CANT_UPDATE_PARAMETER = "Can't update parameter!";
    public static final String ERROR_CANT_GET_BLOCK_FROM_BLOCKCHAIN = "Can't get block from BlockChain!";



    private static boolean useOnlyNewRollbackAlgo = false, zeroblockFixed = false;

    private static void log(boolean good, String message, boolean showTrue) {
        if (good && !showTrue) {
            return;
        }
        // System.err.println("ParaMining ENGINE - " + (good ? "GOOD" : "BAD") + ": " + message);
        if (good)
            Logger.logInfoMessage(message);
        else
            Logger.logErrorMessage(message);
    }

    private Connection conn = null;

    private String JDBC = null;
    private String login = null;
    private String password = null;

    private final BoostMap<Long, Boolean> networkBooster = new BoostMap<>(8192, -1);
    private final BoostMap<Long, Boolean> networkBooster1440 = new BoostMap<>(8192, -1);


    private boolean initialized = false;

    @Override
    public void init() {
        preinit();
    }

    private void preinit() {
        synchronized (LOCK_OBJECT) { // NullPointer fix
            if (initialized) {
                return;
            }
            initialized = true;
            initDB();
            log(true, "DATABASE INITIALIZED", true);
//            commit();
        }
    }

    public ParaEngine(String JDBC, String login, String password) {

        this.JDBC = JDBC;
        this.login = login;
        this.password = password;
    }

    

    private void initDBcreateIndices() throws SQLException {
        update("alter table force add foreign key (block_id) references block(id);");
        update("create unique index para_pk on para(id);");
        update("alter table para add foreign key (parent_id) references para(id);");
        update("create unique index block_pk on block(id);");
        update("create unique index block_height on block(height);");
        update("create unique index force_master on force(txid, to_id);");
        update("create index force_height on force(height);");
        update("create unique index force_stxid on force(stxid);");
        update("create index force_tech on force(tech);");
        update("create index hold_transfer_account_id on hold_transfer(height desc)");
    }

    private void initDBcreateIndicesFinalFix() throws SQLException {
        update("alter table para alter column id bigint not null");
        update("alter table para alter column amount bigint not null default 0");
        update("alter table para alter column balance bigint not null default 0");
        update("alter table para alter column last int not null");

        update("alter table block alter column id bigint not null");
        update("alter table block alter column height int not null");
        update("alter table block alter column fee bigint not null default 0");
        update("alter table block alter column stamp int not null default 0");
        update("alter table block alter column accepted boolean not null default false");

        update("alter table force alter column block_id bigint not null");
        update("alter table force alter column amount bigint not null");
        update("alter table force alter column to_id bigint not null");
        update("alter table force alter column announced boolean not null default false");
        update("alter table force alter column height int not null");
    }

    private void initDB() {
        try {
            ///--- is nxt mesh
            long maxCacheSize = Prizm.getIntProperty("prizm.dbCacheKB");
            if (maxCacheSize == 0) {
                maxCacheSize = Math.max(256, Math.max(16, (Runtime.getRuntime().maxMemory() / (1024 * 1024) - 128)/2))*512;// * 1024;
            }            
            ///
            Class.forName("org.h2.Driver");
            this.conn = DriverManager.getConnection(JDBC+";CACHE_SIZE="+maxCacheSize, login, password);
            try(Statement stmt = this.conn.createStatement()) {
                stmt.executeUpdate("SET DEFAULT_LOCK_TIMEOUT " + (Prizm.getIntProperty("prizm.dbDefaultLockTimeout") * 1000));
                stmt.executeUpdate("SET MAX_MEMORY_ROWS " + Prizm.getIntProperty("prizm.dbMaxMemoryRows"));
            }
            //this.conn.setAutoCommit(false);
            this.conn.setAutoCommit(true);
            //update("begin work;");

            try(PreparedStatement pre = conn.prepareStatement("select * from para where id=-1")){
                pre.executeQuery();
            }

            final int height = BlockchainImpl.getInstance().getHeight();
            final Integer newRollbackHeight = getParameter(FAST_ROLLBACK_ENABLED_HEIGHT);
            if (height == 0 || (newRollbackHeight != null && newRollbackHeight <= height)) {
                log(true, height + " - Using new rollback algorithm as default " + ((newRollbackHeight != null && newRollbackHeight == 0)?"from Genesis block":"from transfer block "+newRollbackHeight), true);
                useOnlyNewRollbackAlgo = true;
            }

            // Update database to support fast rollback
            try( PreparedStatement pre = conn.prepareStatement("select last from force where block_id=-1")){
                    pre.executeQuery();   
            } catch (SQLException ex) {
                if (ex.toString().contains("LAST") || ex.toString().contains("ACTIVATION")) {
                    if (height > 0)
                        log(true, "Updating parabase...", true);
                    update("alter table force add column last int;");
                    update("alter table force add column paratax long default 0;");
                    update("alter table force add column tech boolean not null default false;");
                    update("create index force_tech on force(tech);");
                    update("alter table force_1440 add column paratax long default 0;");
                    update("alter table force_1440 add column tech boolean not null default false;");
                    update("create index force_1440_tech on force_1440(tech);");
                    update("create table activation (para_id bigint primary key, height int not null)");
                    update("alter table activation add foreign key (para_id) references para(id) on delete cascade;");
                    setParameter(PARABASE_FAST_ROLLBACK_UPDATE_HEIGHT, height);
                    setParameter(MIN_FAST_ROLLBACK_HEIGHT, height + 100);
                    setParameter(FAST_ROLLBACK_ENABLED_HEIGHT, height + CACHE_SIZE);
                    if (height > 0)
                        log(true, "Parabase update completed! Fast rollback will be enabled at " + (height + CACHE_SIZE), true);
//                    commit();
                } 
            }
            // Update database to support hold and last_forged_block
            try( PreparedStatement pre = conn.prepareStatement("select hold from para limit 1")){
                pre.executeQuery();
            } catch (SQLException ex) {
                if (ex.toString().contains("HOLD")) {
                    log(true, "Database update started, please wait", true);
                    log(true, "Altering tables...", true);
                    update("alter table para add column last_forged_block_height int not null default 0;");
                    update("alter table para add column hold bigint not null default 0 after balance;");
                    update("create table hold_transfer (id bigint not null, amount bigint not null, height int not null);");
                    update("create index hold_transfer_account_id on hold_transfer(height desc)");
                    setParameter(HOLD_UPDATE_HEIGHT, height);
                    int startHeight = Constants.HOLD_ENABLE_HEIGHT - Constants.HOLD_RANGE;
                    log(true, "Rescanning last blocks from height " + startHeight + "...", true);
                    if (height > startHeight || height == 0) {
                        Map<Long, Integer> blockCreators = new HashMap<>();
                        try (PreparedStatement ps = conn.prepareStatement("select creator_id,height from block where height>=?")) {
                            ps.setInt(1, startHeight);
                            try (ResultSet scanRs = ps.executeQuery()) {
                                while (scanRs.next()) {
                                    long creatorId = scanRs.getLong(1);
                                    int forgedHeight = scanRs.getInt(2);
                                    if (!blockCreators.containsKey(creatorId)) {
                                        blockCreators.put(creatorId, forgedHeight);
                                    } else if (blockCreators.get(creatorId) < forgedHeight) {
                                        blockCreators.put(creatorId, forgedHeight);
                                    }
                                }
                            }
                        }
                        log(true, "Updating " + blockCreators.size() + " forging accounts...", true);
                        for (Long account : blockCreators.keySet()) {
                            setLastForgedBlockHeight(account, blockCreators.get(account));
                        }
                    }
                    log(true, "Parabase update completed!", true);
//                    commit();
                }
            }
            
            // Validate hold integrity
            long badHoldsCounts = 0L;
            if (getParameter(HOLD_INTEGRITY_VALIDATED) == null) { // Do not validate twice (it takes couple of minutes)
                log(true, "Starting database validation", true);
                try( PreparedStatement pre = conn.prepareStatement("select count(hold) from para where hold<0");
                    ResultSet rs = pre.executeQuery();){
                        while (rs.next()) {
                            badHoldsCounts = rs.getLong(1);
                        }
                } catch (SQLException ex) {
                }
                if (badHoldsCounts <= 0) {
                    log(true, "Database validation: OK", true);
                    setParameter(HOLD_INTEGRITY_VALIDATED, height);
                } else {
                    for (int i = 0; i < 5; i++) {
                        log(true, "Database validation: ERROR - INTEGRITY COMPROMISED", true);
                        log(true, "CRITICAL ERROR - Blockchain integrity validation failed", true);
                        log(true, "Database validation: ERROR - DAMAGED BLOCKCHAIN", true);
                        log(true, "CRITICAL ERROR - Re-sync from scratch is required to continue", true);
                    }
                    log(true, "Database validation failed, detected " + badHoldsCounts + " unrecoverable error(s)", true);
                    log(true, "PrizmCore is going to shutdown because blockchain data is corrupted", true);
                    log(true, "Please, delete the \"prizm_db\" directory before restart", true);
                    System.exit(1);
                }
            } else {
                log(true, "Bypassing database validation (already validated earlier)", true);
            }
            
        } catch (SQLException ex) {
            if (ex.toString().contains("PARA")) {
                try {
                    log(true, "Initialize database...", true);
                    update("create table lock (id bigint not null default -1);");
                    update("insert into lock(id) values (-1);");

                    try (PreparedStatement blockStatement = conn.prepareStatement("select id from lock for update"); ResultSet rs = blockStatement.executeQuery()) {
                        while (rs.next()) {
                            rs.getLong(1);
                        }
                    }

                    // Create master tables
                    update("create table para (id bigint not null, parent_id bigint, amount bigint not null default 0, balance bigint not null default 0, hold bigint not null default 0, last int not null, last_forged_block_height int not null default 0)");
                    update("create table block (id bigint not null, height int not null, fee bigint not null default 0, stamp int not null default 0, accepted boolean not null default false, creator_id long not null);");
                    update("create table force (block_id bigint not null, txid bigint, amount bigint not null, to_id bigint not null, announced boolean not null default false, stxid bigint, height int not null, last int, paratax long default 0, tech boolean not null default false);");
                    update("create table activation (para_id bigint primary key, height int not null)");
                    update("create table hold_transfer (id bigint not null, amount bigint not null, height int not null);");
                    update("alter table activation add foreign key (para_id) references para(id) on delete cascade;");

                    initDBcreateIndices();

                    update("create table parameters (key varchar(80) primary key, value varchar);");

                    setParameter(PARABASE_FAST_ROLLBACK_UPDATE_HEIGHT, 0);
                    setParameter(MIN_FAST_ROLLBACK_HEIGHT, 0);
                    setParameter(FAST_ROLLBACK_ENABLED_HEIGHT, 0);
                    setParameter(HOLD_UPDATE_HEIGHT, 0);
                    setParameter(ZEROBLOCK_FIXED, 0);
                    useOnlyNewRollbackAlgo = true;
                    log(true, "Using new rollback algorithm from Genesis block", true);

//                    update("commit work;");
//                    commit();
                    log(true, "Success!", true);
                } catch (SQLException exSQL) {
//                    log(false, ERROR_CANT_INITIALIZE);
                }
            } else {
            }
        } catch (ClassNotFoundException ex) {
//            log(false, ERROR_ERROR);
        }
    }

    private ParaBlock getBlockFromBlockchainWithNoTransactions(int height) {
        // Do nothing!
        BlockImpl block;
        try {
            block = BlockchainImpl.getInstance().getBlockAtHeight(height);
        } catch (RuntimeException ex) {
            block = null;
        }
        if (block == null) {
            return null;
        }
        ParaBlock paraBlock = new ParaBlock();
        paraBlock.setID(block.getId());
        paraBlock.setGeneratorID(block.getGeneratorId());
        paraBlock.setFee(block.getTotalFeeNQT());
        paraBlock.setHeight(block.getHeight());
        paraBlock.setStamp(block.getTimestamp());

        if (block.getTransactions() != null) {
            for (TransactionImpl blockTransaction : block.getTransactions()) {
                try {
                    ParaBlock.Transaction trx = ParaEngine.convert(blockTransaction, block.getHeight());
                    paraBlock.getTransactions().add(trx);
                } catch (ParaMiningException ex) {
                }
            }
        }
        if (paraBlock.getTransactions().isEmpty()) paraBlock.setNoTransactions(true);
        return paraBlock;
    }

    private void addDiff(long amount, long account, Map<Long, Long> diffs) {
        if (diffs.containsKey(account))
            diffs.put(account, diffs.get(account) + amount);
        else
            diffs.put(account, amount);
    }

    private void addDiff(long account, long amount, Integer stamp, Map<Long, Long> diffs, Map<Long, Integer> stamps) {
        addDiff(amount, account, diffs);
        if (!stamps.containsKey(account) || (stamps.containsKey(account) && stamp != null))
            stamps.put(account, stamp);
    }

    private Integer getParameter(String key) {
        Integer value = null;
        try {
            try (PreparedStatement ps = conn.prepareStatement("select value from parameters where key=?")) {
                ps.setString(1, key);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next())
                        value = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            return null;
        }
        if (value == null || value == -1)
            value = null;
        if (value == null)
            log(false, "getParameter: Parameter \"" + key + "\" is null!", true);
        return value;
    }

    private void setParameter(String key, int value) {
        try {
            try (PreparedStatement ps = conn.prepareStatement("merge into parameters values(?,?)")) {
                ps.setString(1, key);
                ps.setInt(2, value);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
        }
        log(true, "($) Set parameter \"" + key + "\" = \"" + value + "\"", true);
    }

    @Override
    public void popLastBlock() {
        final Block lastBlock = BlockchainImpl.getInstance().getLastBlock();
        final int currentHeight = BlockchainImpl.getInstance().getHeight();
        if (!useOnlyNewRollbackAlgo) {
            return;
        }
        networkBooster.clear();
        BASE_TARGET_CACHE.clear();
        preinit();
        boolean holdEnabled = currentHeight >= Constants.HOLD_ENABLE_HEIGHT;
        boolean shouldSetLastForgedBlockHeight = currentHeight >= Constants.HOLD_ENABLE_HEIGHT - Constants.HOLD_RANGE;
        synchronized (LOCK_OBJECT) {
            final List<Long> accountsToDelete = new ArrayList<>();
            final TreeMap<Long, Long> diffs = new TreeMap<>();
            final Set<Long> senders = new HashSet<>();
            List<Long> revertedParaminingTransactions = new ArrayList<>();
            try {
                // Build balance diffs and collect paramining transaction
                if (lastBlock.getTransactions() != null && !lastBlock.getTransactions().isEmpty()) {
                    for (Transaction t : lastBlock.getTransactions()) {
                        senders.add(t.getSenderId());
                        final boolean hasRecipient = t.getRecipientId() != 0L;
                        final boolean isParamining = hasRecipient && t.getSenderId() == Genesis.CREATOR_ID;
                        if (isParamining) {
                            revertedParaminingTransactions.add(t.getId());
                            continue;
                        }
                        final long senderDiff = t.getAmountNQT() + t.getFeeNQT();
                        final long recipientDiff = hasRecipient ? 0L - t.getAmountNQT() : 0L;
                        addDiff(senderDiff, t.getSenderId(), diffs);
                        if (hasRecipient)
                            addDiff(recipientDiff, t.getRecipientId(), diffs);
                    }
                }
                if (lastBlock.getTotalFeeNQT() > 0L) {
                    addDiff(0L - lastBlock.getTotalFeeNQT(), lastBlock.getGeneratorId(), diffs);
                }

                // GET FORCES CREATED IN CURRENT BLOCK
                List<ParaBlock.Payout> forces = new ArrayList<>();           
                try(PreparedStatement request = conn.prepareStatement("select to_id,amount,height,last, paratax from force where height=?")){
                    request.setLong(1, currentHeight);
                    try(ResultSet rs = request.executeQuery()){
                    while (rs.next()) {
                        ParaBlock.Payout force = new ParaBlock.Payout();
                        force.setToID(rs.getLong(1));
                        force.setAmount(rs.getLong(2));
                        force.setHeight(rs.getInt(3));
                        force.setLast(rs.getInt(4));
                        force.setParaTax(rs.getLong(5));
                        forces.add(force);
                    }
                }}

                if (shouldSetLastForgedBlockHeight) {
                    int lastForgedBlockHeight = 0;
                    try( PreparedStatement request = conn.prepareStatement("select max(height) from block where creator_id=? and height<?")){
                        request.setLong(1, lastBlock.getGeneratorId());
                        request.setInt(2, currentHeight);
                        try(ResultSet rs = request.executeQuery()){
                        while (rs.next())
                            lastForgedBlockHeight = rs.getInt(1);
                        }}

                    try(PreparedStatement request = conn.prepareStatement("update para set last_forged_block_height=? where id=?")){
                        request.setInt(1, lastForgedBlockHeight);
                        request.setLong(2, lastBlock.getGeneratorId());
                        request.executeUpdate();
                    }
                }
                
                Map<Long, Long> holdTransfers = new HashMap<>();
                if (holdEnabled) {
                    try( PreparedStatement request = conn.prepareStatement("select id,amount from hold_transfer where height=?")){
                    request.setInt(1, currentHeight);
                    try(ResultSet rs = request.executeQuery()){
                    while (rs.next()) {
                        holdTransfers.put(rs.getLong(1), rs.getLong(2));
                    }
                    }}

                    // FIRST DELETE TRANSFERS
                    try( PreparedStatement request = conn.prepareStatement("delete from hold_transfer where height=?")){
                        request.setInt(1, currentHeight);
                        request.executeUpdate();
                    }
                    
                    // AND THEN PUT THEM ONTO BALANCE
                    for (Long account : holdTransfers.keySet()) {
                        if (account == null)
                            continue;
                        addDiff(-holdTransfers.get(account), account, diffs);
                        try( PreparedStatement request = conn.prepareStatement("update para set hold=? where id=?")){
                            request.setLong(1, holdTransfers.get(account));
                            request.setLong(2, account);
                            request.executeUpdate();
                        }
                    }
                }

                Set<Integer> blockHeights = new HashSet<>();

                // REVERT 'LAST' PARAMETERS AND DELETE FORCES
                int count = 0;
                if (!forces.isEmpty()) {
                    for (ParaBlock.Payout force : forces) {                       
                        try( PreparedStatement request = conn.prepareStatement("update para set last=? where id=?")){
                            request.setLong(1, force.getLast());
                            request.setLong(2, force.getToID());
                            request.executeUpdate();
                        }
                        count++;
                        addDiff(0L - force.getAmount(), force.getToID(), diffs);
                        addDiff(force.getAmount(), Genesis.CREATOR_ID, diffs);
                    }                 
                    try (PreparedStatement trimmer = conn.prepareStatement("delete from force where height>=?")) {
                        trimmer.setInt(1, currentHeight);
                        count = trimmer.executeUpdate();
                    }
                }

                // RE-OPEN SATISFIED FORCES IN PREVIOUS BLOCKS
                if (!revertedParaminingTransactions.isEmpty()) {
                    count = 0;
                    for (Long stxid : revertedParaminingTransactions) {
                        count++;                     
                        try( PreparedStatement request = conn.prepareStatement("select height from force where stxid=?")){
                            request.setLong(1, stxid);
                            try(ResultSet rs = request.executeQuery()){
                            while (rs.next()) {
                                Integer height = rs.getInt(1);
                                if (height != null && height > 0) {
                                    blockHeights.add(height);
                                }
                            }
                            }}                        
                        try( PreparedStatement request = conn.prepareStatement("update force set stxid=? where stxid=?")){
                        request.setNull(1, Types.BIGINT);
                        request.setLong(2, stxid);
                        request.executeUpdate();
                        }
                    }

                }

                // SET PREVIOUS PARABLOCKS AS UNACCEPTED
                if (!blockHeights.isEmpty()) {
                    for (Integer notAcceptedHeight : blockHeights) {
                        try( PreparedStatement request = conn.prepareStatement("update block set accepted=false where height=? and accepted=true")){
                            request.setInt(1, notAcceptedHeight);
                            request.executeUpdate();
                        }
                    }
                }

                // DELETE FUTURE BLOCKS - EXPECTED ONLY 1 BLOCK TO BE DELETED (THE CURRENT ONE)
                count = 0;
                try( PreparedStatement request = conn.prepareStatement("delete from block where height>?")){
                    request.setInt(1, currentHeight - 1);
                    count = request.executeUpdate();
                    }
                if (count != 1) {
                    if (count < 1)
                        log(false, "popLastBlock() - No blocks deleted (must be 1) at " + currentHeight, true);
                    if (count > 1)
                        log(false, "popLastBlock() - Too many blocks deleted: " + count + " (must be 1) at " + currentHeight, true);
                }

                String msg = currentHeight + " <- this block is popped\n\tDiffs: [" + diffs.size() + "]";
                
                // APPLY BALANCE DIFFS
                if (!diffs.isEmpty()) {
                    if (holdEnabled) {
                        for (Long accountId : diffs.keySet()) {
                            int height = 0;
                            long balance = 0l;
                            long hold = 0l;
                            try( PreparedStatement request = conn.prepareStatement("select balance,last_forged_block_height,hold from para where id=?")){
                                request.setLong(1, accountId);
                                try(ResultSet rs = request.executeQuery()){
                                while (rs.next()) {
                                    balance = rs.getLong(1);
                                    height = rs.getInt(2);
                                    hold = rs.getLong(3);
                                }
                                }}
                            long balanceBeforeBlock = balance + diffs.get(accountId);
                            boolean isEnterHoldFromLowerBalance = hold == 0L && diffs.get(accountId) < 0 && balanceBeforeBlock < Constants.HOLD_BALANCE_MIN;
                            boolean isOnHold = height >= currentHeight - Constants.HOLD_RANGE 
                                    && balance >= Constants.HOLD_BALANCE_MIN
                                    && balance <= Constants.HOLD_BALANCE_MAX;
                            if (
                                    isOnHold 
                                 && (!senders.contains(accountId)) 
                                 && (!isEnterHoldFromLowerBalance)
                            ) { 
                                updateHold(accountId, diffs.get(accountId));
                            } else {
                                update(accountId, diffs.get(accountId), null);
                            }
                        }
                    } else {
                        for (Long accountId : diffs.keySet()) {
                            msg = msg + ", " + accountId + " " + diffs.get(accountId);
                            update(accountId, diffs.get(accountId), null);
                        }
                    }
                }
//                log(true, msg, false);

                // FIND ACCOUNTS TO DELETE
                try( PreparedStatement request = conn.prepareStatement("select para_id from activation where height=?")){
                    request.setInt(1, currentHeight);
                    try(ResultSet rs = request.executeQuery()){
                    while (rs.next()) {
                        accountsToDelete.add(rs.getLong(1));
                    }
                    }}

                // DELETE ACTIVATED IN THIS BLOCK ACCOUNTS
                count = 0;
                msg = "\tDeleted accounts: [" + accountsToDelete.size() + "]";
                if (!accountsToDelete.isEmpty()) {
                    for (Long id : accountsToDelete) {
                        msg = msg + ", " + id;
                        try( PreparedStatement request = conn.prepareStatement("delete from para where id=?")){
                            request.setLong(1, id);
                            count = count + request.executeUpdate();
                        }
                    }
                }
//                log(true, msg, false);
//                commit();
            } catch (Exception e) {
                // TODO
                rollback();
                log(false, "CRITICAL - FAILED TO POP LAST BLOCK BECAUSE OF \"" + e.getMessage() + "\"", true);
            }
        }
    }
    
    @Override
    public  void rollbackToBlock(int blockHeight) {
        networkBooster.clear();
    }
    

    private void dropOldDatabases(int height) throws SQLException {
        try(PreparedStatement statement = conn.prepareStatement("drop table para_1440")){
            statement.executeUpdate();
        }
        try(PreparedStatement statement = conn.prepareStatement("drop table force_1440")){
            statement.executeUpdate();
        }
        try(PreparedStatement statement = conn.prepareStatement("drop table block_1440")){
            statement.executeUpdate();
        }
//        commit();
        log(true, "trimDerivedTables: Old database deleted at " + height, true);
    }

    private void trimDerivedTables() throws SQLException {
        final int height = getParamLast();
        
        if (height % CACHE_SIZE != 0 || !useOnlyNewRollbackAlgo)
            return;
        final Integer minFastRollbackHeight = getParameter(MIN_FAST_ROLLBACK_HEIGHT);
        if (minFastRollbackHeight == null) {
            return;
        }
        if (height - minFastRollbackHeight < CACHE_SIZE) {
            int nextTrimHeight = minFastRollbackHeight + CACHE_SIZE;
            nextTrimHeight = ((nextTrimHeight / CACHE_SIZE) + 1) * CACHE_SIZE;
            log(true, "trimDerivedTables: Postponed trimming for " + (nextTrimHeight-height) + " more blocks", true);
            return;
        }
        final int newMinRollbackHeight = height - CACHE_SIZE; // preserve last 820 blocks
        int forces = 0, activations = 0, holdTransfers = 0, deleted=0;       
        try(PreparedStatement statement = conn.prepareStatement("delete from force where height<? and ((stxid is not null) or (stxid is null and tech)) limit " + Constants.BATCH_COMMIT_SIZE)){
            statement.setInt(1, newMinRollbackHeight);
            do {
                deleted = statement.executeUpdate();
                forces += deleted;
//                commit();
            } while (Constants.BATCH_COMMIT_SIZE < deleted);
        }
        try(PreparedStatement statement = conn.prepareStatement("delete from activation where height<? limit " + Constants.BATCH_COMMIT_SIZE)){
            statement.setInt(1, newMinRollbackHeight);
            do {
                deleted = statement.executeUpdate();
                activations += deleted;
//                commit();
            } while (deleted >= Constants.BATCH_COMMIT_SIZE);
        }
        try(PreparedStatement statement = conn.prepareStatement("delete from hold_transfer where height<? limit " + Constants.BATCH_COMMIT_SIZE)){
            statement.setInt(1, newMinRollbackHeight);
            do {
                deleted = statement.executeUpdate();
                holdTransfers += deleted;
//                commit();
            } while (deleted >= Constants.BATCH_COMMIT_SIZE);
        }
        setParameter(MIN_FAST_ROLLBACK_HEIGHT, newMinRollbackHeight);
//        commit();
        log(true, "trimDerivedTables: Trimmed " + forces + " payouts, " + activations + " activations and " + holdTransfers + " hold transfers at " + height, true);
    }
    
    private boolean rewriteWorkingDatabase(int currentBlock) throws SQLException {
        // Do nothing!
        if (useOnlyNewRollbackAlgo) {
            log(false, currentBlock + " - rewriteWorkingDatabase: invoked while the new rollback system is enabled. This should never happen.", true);
            return true;
        } else {
            log(true, currentBlock + " - Using legacy rollback algorithm. Will switch to the new one at height " + getParameter(FAST_ROLLBACK_ENABLED_HEIGHT), true);
        }
        boolean allRight = true;
        System.out.println("=== REINIT WORKING DATABASE - DO NOT STOP PRIZMCORE ===");
        // Database can be damaged if PrizmEngine has been stopped during database rewriting
        // On restart we won't be able to find one of the droppable tables and db rewrite would be impossible
        // So we should try to "drop if exists" instead of "drop"
        update("DROP TABLE IF EXISTS para;");
        update("DROP TABLE IF EXISTS force;");
        update("DROP TABLE IF EXISTS block;");

        update("CREATE TABLE para AS SELECT * FROM para_1440;");
        update("CREATE TABLE force AS SELECT * FROM force_1440;");
        update("CREATE TABLE block AS SELECT * FROM block_1440;");
        initDBcreateIndices();
        initDBcreateIndicesFinalFix();
//        commit();
        int firstBlock = getParamLast()+1;
        if (firstBlock >= currentBlock) {
            return allRight;
        }

        try (PreparedStatement activations = conn.prepareStatement("delete from activations where height>=?")) {
            activations.setInt(1, firstBlock);
            activations.executeUpdate();
        }

        for (int i = firstBlock; i < currentBlock; i++) {
            try {
                ParaBlock paraBlock = getBlockFromBlockchainWithNoTransactions(i);
                if (paraBlock != null) {
                    if (paraBlock.hasNoTransactions()) {
                        insertBlock(paraBlock.getID(), paraBlock.getHeight(), 0, paraBlock.getStamp(), paraBlock.getGeneratorID(), true);
//                        commit();
                    } else {
                        checkInternal(paraBlock, true);
                    }
                }
            } catch (ParaMiningException ex) {
                allRight = false;
            }
        }
        return allRight;
    }

    private int getParamLast() throws SQLException {
        preinit();
        int retval = -1;
        try (PreparedStatement request = conn.prepareStatement("select max(height) from block"); ResultSet rs = request.executeQuery()) {
            if (rs == null) {
                throw new SQLException(ERROR_CANT_UPDATE_PARAMETER + " [select]");
            }
            while (rs.next()) {
                if (rs.getString(1) != null) {
                    retval = rs.getInt(1);
                }
            }
        }
        if (retval < 0) {
            throw new SQLException(ERROR_CANT_UPDATE_PARAMETER + " [<0]");
        }
        return retval;
    }

    private int getParamLast1440() throws SQLException {
        preinit();
        int retval = -1;
        try (PreparedStatement request = conn.prepareStatement("select max(height) from block_1440"); ResultSet rs = request.executeQuery()) {
            if (rs == null) {
                throw new SQLException(ERROR_CANT_UPDATE_PARAMETER + " [select]");
            }
            while (rs.next()) {
                if (rs.getString(1) != null) {
                    retval = rs.getInt(1);
                }
            }
        }
        if (retval < 0) {
            throw new SQLException(ERROR_CANT_UPDATE_PARAMETER + " [<0]");
        }
        return retval;
    }

//    private void commit() {
//        // Do nothing!
//        if (conn == null) {
//            return;
//        }
//        try {
//            update("commit work;");
//            conn.commit();
//            update("begin work;");
//        } catch (SQLException ex) {
////            log(false, ERROR_CANT_COMMIT);
//        }
//    }
    
    private void update(String SQL) throws SQLException {
        try (PreparedStatement pre = conn.prepareStatement(SQL)) {
            pre.executeUpdate();
        }
    }

    private void rollback() {
        // Do nothing!
        if (conn == null) {
//            log (false, ERROR_DATABASE_CLOSED);
            return;
        }
        try {
            update("rollback;");
            conn.rollback();
            update("begin work;");
        } catch (SQLException ex) {
//            log(false, ERROR_CANT_COMMIT);
        }
    }

    private static void updateFix(Connection conn, Conc conc, long amount) throws SQLException {
        try (PreparedStatement updater = conn.prepareStatement(conc.query())) {
            updater.setLong(1, amount);
            updater.executeUpdate();
        }
    }

    private void createAccount(long accountID, Long senderID, int stamp, int height) throws SQLException {
        // Do nothing!
        // Do nothing!
        if (senderID == null) {
            try (PreparedStatement statement = conn.prepareStatement("insert into para(id, last) values (?,?)")) {
                statement.setLong(1, accountID);
                statement.setInt(2, stamp);
                statement.executeUpdate();
            }
        } else {
            try (PreparedStatement statement = conn.prepareStatement("insert into para(id, parent_id, last) values (?,?,?)")) {
                statement.setLong(1, accountID);
                statement.setLong(2, senderID);
                statement.setInt(3, stamp);
                statement.executeUpdate();
            }
        }
        try (PreparedStatement activation = conn.prepareStatement("insert into activation(para_id, height) values (?,?)")) {
            activation.setLong(1, accountID);
            activation.setInt(2, height);
            activation.executeUpdate();
        }
    }

    private void createAccount1440(long accountID, Long senderID, int stamp) throws SQLException {
        // Do nothing!
        // Do nothing!
        if (senderID == null) {
            try (PreparedStatement statement = conn.prepareStatement("insert into para_1440(id, last) values (?,?)")) {
                statement.setLong(1, accountID);
                statement.setInt(2, stamp);
                statement.executeUpdate();
            }
        } else {
            try (PreparedStatement statement = conn.prepareStatement("insert into para_1440(id, parent_id, last) values (?,?,?)")) {
                statement.setLong(1, accountID);
                statement.setLong(2, senderID);
                statement.setInt(3, stamp);
                statement.executeUpdate();
            }
        }
    }

    private void createNetwork(long receiverID, long senderID, int stamp, int height) throws SQLException {
        Long receiverIDObj = receiverID;
        if (networkBooster.containsKey(receiverIDObj)) {
            return;
        }
        boolean receiver = false;
        try (PreparedStatement statement = conn.prepareStatement("select id from para where id=?")) {
            statement.setLong(1, receiverID);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    receiver = true;
                }
            }
        }
        if (stamp == 0) { // Genesis block
            Long senderIDObj = senderID;
            if (!networkBooster.containsKey(senderIDObj)) {
                createAccount(senderID, null, stamp, 0);
                networkBooster.put(senderIDObj, true);
            }
        }
        if (!receiver) {
            createAccount(receiverID, senderID, stamp, height);
        }
        networkBooster.put(receiverIDObj, true);

    }

    private void createNetwork1440(long receiverID, long senderID, int stamp) throws SQLException {
        Long receiverIDObj = receiverID;
        if (networkBooster1440.containsKey(receiverIDObj)) {
            return;
        }
        boolean receiver = false;
        try (PreparedStatement statement = conn.prepareStatement("select id from para_1440 where id=?")) {
            statement.setLong(1, receiverID);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long accountID = rs.getLong(1);
                    if (accountID == receiverID) {
                        receiver = true;
                    }
                }
            }
        }
        if (stamp == 0) { // Genesis block
            Long senderIDObj = senderID;
            if (!networkBooster1440.containsKey(senderIDObj)) {
                createAccount1440(senderID, null, stamp);
                networkBooster1440.put(senderIDObj, true);
            }
        }
        if (!receiver) {
            createAccount1440(receiverID, senderID, stamp);
        }
        networkBooster1440.put(receiverIDObj, true);
    }

    private long getGenesisEmission() throws SQLException {
        long retval = 0l;
        try (PreparedStatement statement = conn.prepareStatement("SELECT balance FROM PARA where id=?")) {
            statement.setLong(1, Genesis.CREATOR_ID);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    retval = rs.getLong(1);
                }
            }
        }
        return retval;
    }

    private int getCurrentParaHeight() throws SQLException {
        int retval = 0;
        try (PreparedStatement statement = conn.prepareStatement("select max(height) from block"); ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                retval = rs.getInt(1);
            }
        }
        return retval;
    }

    private long getGenesisEmission1440() throws SQLException {
        long retval = 0l;
        try (PreparedStatement statement = conn.prepareStatement("SELECT balance FROM PARA_1440 where id=?")) {
            statement.setLong(1, Genesis.CREATOR_ID);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    retval = rs.getLong(1);
                }
            }
        }
        return retval;
    }

    @Override
    public ParaMetrics getMetricsForAccount(long accountID, int stamp, boolean setParaTax, int height, boolean holdEnabled) throws SQLException {
        ParaMetrics metrics = new ParaMetrics();

        metrics.setBeforeStamp(stamp);
        metrics.setAfterStamp(stamp);
        if (setParaTax) ParaMetrics.setParataxPercent(metrics, height, getGenesisEmission(), false);

        try (PreparedStatement statement = conn.prepareStatement("select id,parent_id,amount,balance,last,hold,last_forged_block_height from para where id=?")) {
            statement.setLong(1, accountID);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    if (rs.getLong(1) == accountID) {
                        metrics.setBeforeStamp(rs.getInt("last"));
                        metrics.setBalance(rs.getLong("balance"));
                        metrics.setAmount(rs.getLong("amount"));
                        metrics.setHold(rs.getLong("hold"));
                        metrics.setLastForgedBlockHeight(rs.getInt("last_forged_block_height"));
                        metrics.setAccountID(accountID);
                        boolean isOnHold = holdEnabled && metrics.isOnHoldAtHeight(height);
                        if (height < Constants.ENABLE_COMPOUND_AND_2X_PARATAX) {
                            metrics.calculateOrdinaryInterest();
                        } else {
                            metrics.calculateCompoundInterest(isOnHold);
                        }
                    }
                }
            }
        }
        return metrics;
    }

    private ParaMetrics getMetricsForAccount1440(long accountID, int stamp, boolean setParaTax, int height, boolean holdEnabled) throws SQLException {
        // Do nothing!
        // Do nothing!

        ParaMetrics metrics = new ParaMetrics();

        metrics.setBeforeStamp(stamp);
        metrics.setAfterStamp(stamp);
        if (setParaTax) ParaMetrics.setParataxPercent(metrics, height, getGenesisEmission1440(), false);

        try (PreparedStatement statement = conn.prepareStatement("select id,parent_id,amount,balance,last,hold,last_forged_block_height from para_1440 where id=?")) {
            statement.setLong(1, accountID);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    if (rs.getLong(1) == accountID) {
                        metrics.setBeforeStamp(rs.getInt("last"));
                        metrics.setBalance(rs.getLong("balance"));
                        metrics.setAmount(rs.getLong("amount"));
                        metrics.setHold(rs.getLong("hold"));
                        metrics.setLastForgedBlockHeight(rs.getInt("last_forged_block_height"));
                        boolean isOnHold = holdEnabled && metrics.isOnHoldAtHeight(height);
                        if (height < Constants.ENABLE_COMPOUND_AND_2X_PARATAX) {
                            metrics.calculateOrdinaryInterest();
                        } else {
                            metrics.calculateCompoundInterest(isOnHold);
                        }
                    }
                }
            }
        }
        return metrics;
    }

    private List<ParaBlock.Payout> insertBlock(final long blockID, int height, long fee, int stamp, long creatorID, boolean withFinishedState) throws SQLException {
        // Do nothing!
        boolean hasTransaction = false;

        try ( // Do nothing!
                PreparedStatement query = conn.prepareStatement("select id from block where id=? and height=?")) {
            query.setLong(1, blockID);
            query.setLong(2, height);
            try (ResultSet rs = query.executeQuery()) {
                while (rs.next()) {
                    hasTransaction = true;
                }
            }
        }
        if (hasTransaction) {
            List<ParaBlock.Payout> retval = new ArrayList<>();
            try (PreparedStatement request = conn.prepareStatement("select block_id,txid,amount,to_id,paratax from force where not tech and block_id=?")) {
                request.setLong(1, blockID);
                try (ResultSet reqres = request.executeQuery()) {
                    while (reqres.next()) {
                        ParaBlock.Payout payout = new ParaBlock.Payout();
                        payout.setBlockID(reqres.getLong(1));
                        payout.setTxID(reqres.getString(2) != null ? reqres.getLong(2) : null);
                        payout.setHeight(height);
                        payout.setAmount(reqres.getLong(3));
                        payout.setToID(reqres.getLong(4));
                        payout.setParaTax(reqres.getLong(5));
                        retval.add(payout);
                    }
                }
            }
            return retval;
        }

        int count;
        try (PreparedStatement statement = conn.prepareStatement("insert into block (id, height, fee, stamp, creator_id"+(withFinishedState?", accepted":"")+") values (?,?,?,?,?"+(withFinishedState?",true":"")+")")) {
            statement.setLong(1, blockID);
            statement.setLong(2, height);
            statement.setLong(3, fee);
            statement.setInt(4, stamp);
            statement.setLong(5, creatorID);
            count = statement.executeUpdate();
        }
        if (count < 1) {
            throw new SQLException(ERROR_ALREADY);
        }
        boolean shouldSetLastForgedBlockHeight = height >= Constants.HOLD_ENABLE_HEIGHT - Constants.HOLD_RANGE;
        if (shouldSetLastForgedBlockHeight && withFinishedState) {
            setLastForgedBlockHeight(creatorID, height);
        }
        if (withFinishedState) { // Empty block
            if (height >= Constants.INITIAL_BASE_TARGET_TRANSITION_700_BEGINS) {
                try {
                    insertBaseTargetIntoDatabase(height, blockID);
                } catch (SQLException ex) {
                    log(false, "Can't insert BASE_TARGET for " + height + " ("+blockID+")", true);
                }
            }
        }
        return null;
    }

    public int getBaseTargetStart(int height) {
        return (height / Constants.BASE_TARGET_STEP) * Constants.BASE_TARGET_STEP;
    }

    public boolean isBaseTargetRegionBegins(int height) {
        return getBaseTargetStart(height) == height;
    }
    

    
    public static long calculateBaseTarget(long genesisBalance) {
        BigInteger counter = BigInteger.valueOf(genesisBalance);
        counter = counter.divide(BigInteger.valueOf(100));
        return BigInteger.valueOf(Constants.INITIAL_BASE_TARGET_GENERAL).multiply(BigInteger.valueOf(Constants.INITIAL_BASE_TARGET_BASIC)).divide(counter).longValue();
    }
    
    private static final Map<Integer, Long> BASE_TARGET_CACHE = new TreeMap<>();
    
    @Override
    public long getBaseTarget(int height) throws Exception {
        synchronized(LOCK_OBJECT) {
            return getBaseTargetInternal(height);
        }
    }
    
    private static final int HEIGHT_OFFSET = 900; // xxxx900 height must be recorded only once.
    
    private void insertBaseTargetIntoDatabase(int heightIn, long blockID) throws SQLException {
        
        
        int height = ((heightIn / 1000) * 1000)+1000;
        int offset = (height + HEIGHT_OFFSET - 1000);
        if (offset != heightIn) return;

        long genesisBalance = Math.abs(getGenesisEmission());
        long baseTarget = calculateBaseTarget(genesisBalance);

        
        boolean hasData = false;
        try(PreparedStatement query = conn.prepareStatement("select block_id from basetarget where height=?")){
            query.setInt(1, height);
            try(ResultSet rs = query.executeQuery()){
            while (rs.next()) {
                hasData = true;
            }
            }}

        if (hasData) {
            try(PreparedStatement query = conn.prepareStatement("update basetarget set target=?,block_id=? where height=? and block_id<>?")){
            query.setLong(1, baseTarget);
            query.setLong(2, blockID);
            query.setLong(3, height);
            query.setLong(4, blockID);
            if (query.executeUpdate()>0) BASE_TARGET_CACHE.clear();
            }
        } else {
            try(PreparedStatement query = conn.prepareStatement("insert into basetarget (height, block_id, target) values (?,?,?)")){
            query.setInt(1, height);
            query.setLong(2, blockID);
            query.setLong(3, baseTarget);
            if (query.executeUpdate()>0) BASE_TARGET_CACHE.clear();
            }
        }
//        commit();
    }
    
    private long getBaseTargetInternal(int height) throws Exception {

        
        int start = getBaseTargetStart(height);
        long baseTargetSaved = -1;
        
        if (BASE_TARGET_CACHE.containsKey(start)) {
            return BASE_TARGET_CACHE.get(start);
        }
        try {
            try (PreparedStatement query = conn.prepareStatement("select target from basetarget where height=?")) {
                query.setInt(1, start);
                try (ResultSet rs = query.executeQuery()) {
                    while (rs.next()) {
                        baseTargetSaved = rs.getLong(1);
                    }
                }
            }
            if (baseTargetSaved < 0) throw new Exception("Para database (basetarget) is broken!");
            BASE_TARGET_CACHE.put(start, baseTargetSaved);
            return baseTargetSaved;
        } catch (SQLException ex) {
        } 
        throw new Exception("Something wrong with (basetarget) table.");
    }

    private List<ParaBlock.Payout> insertBlock1440(final long blockID, int height, long fee, int stamp, long creatorID, boolean withFinishedState) throws SQLException {
        // Do nothing!
        boolean hasTransaction = false;

        try ( // Do nothing!
                PreparedStatement query = conn.prepareStatement("select id from block_1440 where id=? and height=?")) {
            query.setLong(1, blockID);
            query.setLong(2, height);
            try (ResultSet rs = query.executeQuery()) {
                while (rs.next()) {
                    hasTransaction = true;
                }
            }
        }
        if (hasTransaction) {
            List<ParaBlock.Payout> retval = new ArrayList<>();
            try (PreparedStatement request = conn.prepareStatement("select block_id,txid,amount,to_id,paratax from force_1440 where not tech and block_id=?")) {
                request.setLong(1, blockID);
                try (ResultSet reqres = request.executeQuery()) {
                    while (reqres.next()) {
                        ParaBlock.Payout payout = new ParaBlock.Payout();
                        payout.setBlockID(reqres.getLong(1));
                        payout.setTxID(reqres.getString(2) != null ? reqres.getLong(2) : null);
                        payout.setHeight(height);
                        payout.setAmount(reqres.getLong(3));
                        payout.setToID(reqres.getLong(4));
                        payout.setParaTax(reqres.getLong(5));
                        retval.add(payout);
                    }
                }
            }
            return retval;
        }

        int count;
        try (PreparedStatement statement = conn.prepareStatement("insert into block_1440 (id, height, fee, stamp, creator_id"+(withFinishedState?", accepted":"")+") values (?,?,?,?,?"+(withFinishedState?",true":"")+")")) {
            statement.setLong(1, blockID);
            statement.setLong(2, height);
            statement.setLong(3, fee);
            statement.setInt(4, stamp);
            statement.setLong(5, creatorID);
            count = statement.executeUpdate();
        }
        if (count < 1) {
            throw new SQLException(ERROR_ALREADY);
        }
        return null;
    }
    
//    private List<ParaBlock.Payout> getUnpayedParaTransactions(int height, int limit) throws SQLException {
//        // Do nothing!
//        boolean hasTransaction = false;
//        TreeMap<Long, Integer> blocksForSelect = new TreeMap<>();
//
//        // Do nothing!
////        PreparedStatement query = conn.prepareStatement("select id,height from block where height<=? and accepted=false");
//        PreparedStatement query;
//        if (height % 10000 == 0) {
//            query = conn.prepareStatement("select id,height from block where height<=? and accepted=false");
//        } else {
//            query = conn.prepareStatement("select id,height from block where height<=? and height>=? and accepted=false");
//            query.setLong(2, height - CACHE_DEEP);
//        }
//        query.setLong(1, height - 10);
//        try (ResultSet rs = query.executeQuery()) {
//            while (rs.next()) {
//                long currentID = rs.getLong(1);
//                if (!blocksForSelect.containsKey(currentID)) {
//                    blocksForSelect.put(rs.getLong(1),rs.getInt(2));
//                }
//                if (!hasTransaction) {
//                    hasTransaction = true;
//                }
//            }
//        }
//        query.close();
//        if (hasTransaction) {
//            List<ParaBlock.Payout> retval = new ArrayList<>();
//            for (Entry<Long,Integer> block : blocksForSelect.entrySet()) {
//                try (PreparedStatement request = conn.prepareStatement("select block_id,txid,amount,to_id,paratax from force where not tech and block_id=? and stxid is null")) {
//                    request.setLong(1, block.getKey());
//                    ResultSet reqres = request.executeQuery();
//                    while (reqres.next()) {
//                        ParaBlock.Payout payout = new ParaBlock.Payout();
//                        payout.setBlockID(reqres.getLong(1));
//                        payout.setTxID(reqres.getString(2) != null ? reqres.getLong(2) : null);
//                        payout.setHeight(block.getValue());
//                        payout.setAmount(reqres.getLong(3));
//                        payout.setToID(reqres.getLong(4));
//                        payout.setParaTax(reqres.getLong(5));
//                        retval.add(payout);
//                    }
//                    reqres.close();
//                }
//            }
//            if (retval.size() > limit) {
//                List<ParaBlock.Payout> retvalLimited = new ArrayList<>();
//                retvalLimited.addAll(retval.subList(0, limit));
//                return retvalLimited;
//            }
//            return retval;
//        }
//        return new ArrayList<>();
//    }
//
//    private List<ParaBlock.Payout> getUnpayedParaTransactions1440(int height, int limit) throws SQLException {
//        // Do nothing!
//        boolean hasTransaction = false;
//        TreeMap<Long, Integer> blocksForSelect = new TreeMap<>();
//
//        // Do nothing!
////        PreparedStatement query = conn.prepareStatement("select id,height from block_1440 where height<=? and accepted=false");
//        PreparedStatement query;
//        if (height % 10000 == 0) {
//            query = conn.prepareStatement("select id,height from block_1440 where height<=? and accepted=false");
//        } else {
//            query = conn.prepareStatement("select id,height from block_1440 where height<=? and height>=? and accepted=false");
//            query.setLong(2, height - CACHE_DEEP);
//        }
//        query.setLong(1, height - 10);
//        try (ResultSet rs = query.executeQuery()) {
//            while (rs.next()) {
//                long currentID = rs.getLong(1);
//                if (!blocksForSelect.containsKey(currentID)) {
//                    blocksForSelect.put(rs.getLong(1),rs.getInt(2));
//                }
//                if (!hasTransaction) {
//                    hasTransaction = true;
//                }
//            }
//        }
//        query.close();
//        if (hasTransaction) {
//            List<ParaBlock.Payout> retval = new ArrayList<>();
//            for (Entry<Long,Integer> block : blocksForSelect.entrySet()) {
//                try (PreparedStatement request = conn.prepareStatement("select block_id,txid,amount,to_id,paratax from force_1440 where not tech and block_id=? and stxid is null")) {
//                    request.setLong(1, block.getKey());
//                    try (ResultSet reqres = request.executeQuery()) {
//                        while (reqres.next()) {
//                            ParaBlock.Payout payout = new ParaBlock.Payout();
//                            payout.setBlockID(reqres.getLong(1));
//                            payout.setTxID(reqres.getString(2) != null ? reqres.getLong(2) : null);
//                            payout.setHeight(block.getValue());
//                            payout.setAmount(reqres.getLong(3));
//                            payout.setToID(reqres.getLong(4));
//                            payout.setParaTax(reqres.getLong(5));
//                            retval.add(payout);
//                        }
//                    }
//                }
//            }
//            if (retval.size() > limit) {
//                List<ParaBlock.Payout> retvalLimited = new ArrayList<>();
//                retvalLimited.addAll(retval.subList(0, limit));
//                return retvalLimited;
//            }            
//            return retval;
//        }
//        return new ArrayList<>();
//    }

    private void insertForce(long blockID, Long txID, long amount, long toID, int height, long paraTax)  {
        try {
            int last = -1;
            try(PreparedStatement statement = conn.prepareStatement("select last from para where id=? limit 1")){
                statement.setLong(1, toID);
                try(ResultSet rs = statement.executeQuery()){
                while (rs.next()) {
                    last = rs.getInt(1);
                }
                }}
            int count=0;
            try(PreparedStatement statement = conn.prepareStatement(amount>0?
                    "insert into force (block_id, txid, amount, to_id, height, last, paratax) values (?,?,?,?,?,?,?)":
                    "insert into force (block_id, txid, amount, to_id, height, last, paratax, tech) values (?,?,?,?,?,?,?,?)")){
                statement.setLong(1, blockID);
                if (txID != null) {
                    statement.setLong(2, txID);
                } else {
                    statement.setNull(2, Types.BIGINT);
                }
                statement.setLong(3, amount);
                statement.setLong(4, toID);
                statement.setInt(5, height);
                statement.setInt(6, last);
                statement.setLong(7, paraTax);
                if (amount == 0) {
                    statement.setBoolean(8, true);
                }
                count = statement.executeUpdate();
            }
            if (count < 1) {
                throw new SQLException(ERROR_ALREADY);
            }
        } catch (SQLException ex) {}
    }

    private void insertForce1440(long blockID, Long txID, long amount, long toID, int height, long paraTax) {
        // Do nothing!
        // Do nothing!
        try {

            int count;
            try (PreparedStatement statement = conn.prepareStatement(amount > 0 ?
                    "insert into force_1440 (block_id, txid, amount, to_id, height, paratax) values (?,?,?,?,?,?)":
                    "insert into force_1440 (block_id, txid, amount, to_id, height, paratax, tech) values (?,?,?,?,?,?,?)")) {
                statement.setLong(1, blockID);
                if (txID != null) {
                    statement.setLong(2, txID);
                } else {
                    statement.setNull(2, Types.BIGINT);
                }   statement.setLong(3, amount);
                statement.setLong(4, toID);
                statement.setInt(5, height);
                statement.setLong(6, paraTax);
                if (amount == 0) {
                    statement.setBoolean(7, true);
                }   count = statement.executeUpdate();
            }
            if (count < 1) {
                throw new SQLException(ERROR_ALREADY);
            }            
        } catch (SQLException ex) {}
    }

    private boolean checkForce(ParaBlock.Transaction trx) throws SQLException {
        // Do nothing!
        if (trx == null) return false;
        // Do nothing!
        int count = 0;
        Long stxid = null;
        boolean found = false;
        if (trx.getType() != ParaBlock.Type.PARAMINING) {
            throw new SQLException(ERROR_INVALID_TRANSACTION);
        }
        if (trx.getParaTxID() == null) {
            try (PreparedStatement request = conn.prepareStatement("select stxid from force where not tech and txid is null and amount=? and to_id=?")) {
                request.setLong(1, trx.getAmount());
                request.setLong(2, trx.getReceiver());
                try (ResultSet rs = request.executeQuery()) {
                    while (rs.next()) {
                        stxid = rs.getString(1) != null ? rs.getLong(1) : null;
                        found = true;
                    }
                }
            }
            if (found && stxid == null) {
                try (PreparedStatement statement = conn.prepareStatement("update force set stxid=? where not tech and stxid is null and txid is null and amount=? and to_id=?")) {
                    statement.setLong(1, trx.getID());
                    statement.setLong(2, trx.getAmount());
                    statement.setLong(3, trx.getReceiver());
                    count = statement.executeUpdate();
                }

            }
            if (found && stxid != null) {
                if (stxid == trx.getID()) {
                    count = 1;
                }
            }
        } else {
            try (PreparedStatement request = conn.prepareStatement("select stxid from force where not tech and txid=? and amount=? and to_id=?")) {
                request.setLong(1, trx.getParaTxID());
                request.setLong(2, trx.getAmount());
                request.setLong(3, trx.getReceiver());
                try (ResultSet rs = request.executeQuery()) {
                    while (rs.next()) {
                        stxid = rs.getString(1) != null ? rs.getLong(1) : null;
                        found = true;
                    }
                }
            }
            if (found && stxid == null) {
                try (PreparedStatement statement = conn.prepareStatement("update force set stxid=? where not tech and stxid is null and txid=? and amount=? and to_id=?")) {
                    statement.setLong(1, trx.getID());
                    statement.setLong(2, trx.getParaTxID());
                    statement.setLong(3, trx.getAmount());
                    statement.setLong(4, trx.getReceiver());
                    count = statement.executeUpdate();
                }
            }
            if (found && stxid != null) {
                if (stxid == trx.getID()) {
                    count = 1;
                }
            }
        }
        return count == 1;
    }

    private boolean checkForce1440(ParaBlock.Transaction trx) throws SQLException {
        // Do nothing!
        if (trx == null) return false;
        // Do nothing!
        int count = 0;
        Long stxid = null;
        boolean found = false;
        if (trx.getType() != ParaBlock.Type.PARAMINING) {
            throw new SQLException(ERROR_INVALID_TRANSACTION);
        }
        if (trx.getParaTxID() == null) {
            try (PreparedStatement request = conn.prepareStatement("select stxid from force_1440 where not tech and txid is null and amount=? and to_id=?")) {
                request.setLong(1, trx.getAmount());
                request.setLong(2, trx.getReceiver());
                try (ResultSet rs = request.executeQuery()) {
                    while (rs.next()) {
                        stxid = rs.getString(1) != null ? rs.getLong(1) : null;
                        found = true;
                    }
                }
            }
            if (found && stxid == null) {
                try (PreparedStatement statement = conn.prepareStatement("update force_1440 set stxid=? where not tech and stxid is null and txid is null and amount=? and to_id=?")) {
                    statement.setLong(1, trx.getID());
                    statement.setLong(2, trx.getAmount());
                    statement.setLong(3, trx.getReceiver());
                    count = statement.executeUpdate();
                }
            }
            if (found && stxid != null) {
                if (stxid == trx.getID()) {
                    count = 1;
                }
            }
        } else {
            try (PreparedStatement request = conn.prepareStatement("select stxid from force_1440 where not tech and txid=? and amount=? and to_id=?")) {
                request.setLong(1, trx.getParaTxID());
                request.setLong(2, trx.getAmount());
                request.setLong(3, trx.getReceiver());
                try (ResultSet rs = request.executeQuery()) {
                    while (rs.next()) {
                        stxid = rs.getString(1) != null ? rs.getLong(1) : null;
                        found = true;
                    }
                }
            }
            if (found && stxid == null) {
                try (PreparedStatement statement = conn.prepareStatement("update force_1440 set stxid=? where not tech and stxid is null and txid=? and amount=? and to_id=?")) {
                    statement.setLong(1, trx.getID());
                    statement.setLong(2, trx.getParaTxID());
                    statement.setLong(3, trx.getAmount());
                    statement.setLong(4, trx.getReceiver());
                    count = statement.executeUpdate();
                }
            }
            if (found && stxid != null) {
                if (stxid == trx.getID()) {
                    count = 1;
                }
            }
        }
        return count == 1;
    }

    private boolean checkAnnounceCanReceive(ParaBlock.Transaction trx) throws SQLException {
        // Do nothing!
        if (trx.getType() != ParaBlock.Type.PARAMINING) {
            return true;
        }
        boolean success;
        // Do nothing!
        if (trx.getParaTxID() != null) {
            try (PreparedStatement statement = conn.prepareStatement("update force set announced=true where not tech and announced=false and block_id=? and txid=? and amount=? and to_id=?")) {
                statement.setLong(1, trx.getParaBlockID());
                statement.setLong(2, trx.getParaTxID());
                statement.setLong(3, trx.getAmount());
                statement.setLong(4, trx.getReceiver());
                success = (statement.executeUpdate() == 1);
            }
        } else {
            try (PreparedStatement statement = conn.prepareStatement("update force set announced=true where not tech and announced=false and block_id=? and txid is null and amount=? and to_id=?")) {
                statement.setLong(1, trx.getParaBlockID());
                statement.setLong(2, trx.getAmount());
                statement.setLong(3, trx.getReceiver());
                success = (statement.executeUpdate() == 1);
            }
        }
        return success;
    }


    private void checkBlockIsSuccess(long blockID) throws SQLException {
        // Do nothing!
        // Do nothing!
        int openParaminingTransactions = 0;
        try(PreparedStatement statement = conn.prepareStatement("select count(*) from force where not tech and block_id=? and stxid is null")){
            statement.setLong(1, blockID);
            try(ResultSet rs = statement.executeQuery()){
            while (rs.next()) {
                openParaminingTransactions = rs.getInt(1);
            }
            }}

        if (openParaminingTransactions > 0) {
            return;
        }
        try(PreparedStatement statement = conn.prepareStatement("update block set accepted=true where id=? and accepted=false")){
            statement.setLong(1, blockID);
            statement.executeUpdate();
        }
    }

    private void checkBlockIsSuccess1440(long blockID) throws SQLException {
        // Do nothing!
        // Do nothing!
        int openParaminingTransactions = 0;
        try(PreparedStatement statement = conn.prepareStatement("select count(*) from force_1440 where not tech and block_id=? and stxid is null")){
        statement.setLong(1, blockID);
            try(ResultSet rs = statement.executeQuery()){
                while (rs.next()) {
                    openParaminingTransactions = rs.getInt(1);
                }
            }}

        if (openParaminingTransactions > 0) {
            return;
        }

        try(PreparedStatement statement = conn.prepareStatement("delete from force where not tech and block_id=?")){
            statement.setLong(1, blockID);
            statement.executeUpdate();
        }

        try(PreparedStatement statement = conn.prepareStatement("delete from force_1440 where not tech and block_id=?")){
            statement.setLong(1, blockID);
            statement.executeUpdate();
        }

        try(PreparedStatement statement = conn.prepareStatement("update block_1440 set accepted=true where not tech and id=? and accepted=false")){
            statement.setLong(1, blockID);
            statement.executeUpdate();
        }
    }
    
    private void insertHoldTransfer(long account, long amount, int height) throws SQLException {
        
            try (PreparedStatement updater = conn.prepareStatement("insert into hold_transfer values (?,?,?)")) {
                updater.setLong(1, account);
                updater.setLong(2, amount);
                updater.setInt(3, height);
                updater.executeUpdate();
                   } catch (SQLException ex) {
            throw ex;
        }
    }
    
    private void setLastForgedBlockHeight(long account, int height) throws SQLException {
        try (PreparedStatement updater = conn.prepareStatement("update para set last_forged_block_height=? where id=?")) {
            updater.setInt(1, height);
            updater.setLong(2, account);
            updater.executeUpdate();
        }
    }

    private void updateHold(long ID, long diff) throws Exception {
            try (PreparedStatement updater = conn.prepareStatement("update para set hold=hold+? where id=?")) {
                updater.setLong(1, diff);
                updater.setLong(2, ID);
                updater.executeUpdate();
            
        } catch (SQLException ex) {
            throw ex;
        }
    }
    
    private void update(long ID, long diff, Integer stamp) throws Exception {
        List<HeapStore> heaps = new ArrayList<>();

        
            try (PreparedStatement values = conn.prepareStatement("set @value1 = ?")) {
                values.setLong(1, ID);
                values.executeUpdate();
            try (PreparedStatement statement = conn.prepareStatement("""
                                                                     WITH LINK(ID, PARENT_ID, LEVEL) AS (
                                                                         SELECT ID, PARENT_ID, 0 FROM PARA WHERE ID = @value1
                                                                         UNION ALL
                                                                         SELECT PARA.ID, PARA.PARENT_ID, LEVEL + 1
                                                                         FROM LINK INNER JOIN PARA ON LINK.PARENT_ID = PARA.ID AND LINK.LEVEL < 88
                                                                      )
                                                                      select
                                                                        link.id,
                                                                        link.parent_id,
                                                                        link.level
                                                                     from link""");
                    ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    HeapStore item = new HeapStore(rs.getLong(1), rs.getLong(2), rs.getLong(3));
                    heaps.add(item);
                }
            }
            

            Conc conc = null;
            for (HeapStore item : heaps) {
                if (item.getLevel() < 1) {
                    continue;
                }
                if (conc == null) {
                    conc = new Conc();
                }
                if (!conc.add(item.getBasic())) {
                    updateFix(conn, conc, diff);
                    conc = null;
                }
            }
            if (conc != null) {
                updateFix(conn, conc, diff);
                conc = null;
            }

            if (stamp != null) {
                    try (PreparedStatement updater = conn.prepareStatement("update para set balance=balance+?, last=? where id=?")) {
                        updater.setLong(1, diff);
                        updater.setLong(2, stamp);
                        updater.setLong(3, ID);
                        updater.executeUpdate();
                    }
            } else {
                    try (PreparedStatement updater = conn.prepareStatement("update para set balance=balance+? where id=?")) {
                        updater.setLong(1, diff);
                        updater.setLong(2, ID);
                        updater.executeUpdate();
                    }
            }

        } catch (SQLException ex) {
//            log(false, ERROR_ERROR);
            throw ex;
        }
    }


    public static ParaBlock.Transaction convert(TransactionImpl transaction) throws ParaMiningException {
        return convert(transaction, 1000);
    }

    public static ParaBlock.Transaction convert(TransactionImpl transaction, int height) throws ParaMiningException {
        if (transaction == null) {
            throw new ParaMiningException("NULL Transaction!");
        }
        ParaBlock.Transaction retval = new ParaBlock.Transaction();
        retval.setID(transaction.getId());
        retval.setAmount(transaction.getAmountNQT());
        retval.setFee(transaction.getFeeNQT());
        retval.setReceiver(transaction.getRecipientId());
        retval.setSender(transaction.getSenderId());
        retval.setStamp(transaction.getTimestamp());
        if (transaction.getSenderId() == Genesis.CREATOR_ID) {
            retval.setType(ParaBlock.Type.PARAMINING);
            if (height > 0) {
                ParaBlock.ParaParams paraParams = ParaEngine.getParaParams(transaction);
                if (!paraParams.isValid()) {
                    throw new ParaMiningException("Invalid PARAMINING Transaction!");
                }
                retval.setParaBlockID(paraParams.getBlockID());
                retval.setParaTxID(paraParams.getBlockTxID());
            }
        } else {
            retval.setType(ParaBlock.Type.ORDINARY);
        }
        return retval;
    }

    private static ParaBlock.ParaParams getParaParams(TransactionImpl transaction) {
        ParaBlock.ParaParams retval = new ParaBlock.ParaParams();
        if (transaction == null
                || transaction.getSenderId() != Genesis.CREATOR_ID
                || transaction.getAppendages(false) == null
                || transaction.getAppendages(false).isEmpty()
                || transaction.getFeeNQT() != 0) {
            return retval;
        }
        JSONParser parser = new JSONParser();
        for (Appendix.AbstractAppendix infos : transaction.getAppendages(false)) {
            JSONObject json;
            try {
                if (infos != null && infos.getJSONObject() != null && infos.getJSONObject().get("message") != null) {
                    json = (JSONObject) parser.parse(infos.getJSONObject().get("message").toString());
                    if (json == null
                            || json.get(Constants.IN_BLOCK_ID) == null
                            || json.get(Constants.IN_BLOCK_HEIGHT) == null) {
                        continue;
                    }
                    retval.setBlockID(Long.parseLong(json.get(Constants.IN_BLOCK_ID).toString()));
                    if (json.get(Constants.IN_TRANSACT_ID) != null) {
                        retval.setBlockTxID(Long.valueOf(json.get(Constants.IN_TRANSACT_ID).toString()));
                    }
                    retval.setValid(true);
                    return retval;
                }
            } catch (NumberFormatException | ParseException ex) {
                return new ParaBlock.ParaParams();
            }
        }
        return retval;
    }

    private static final Object LOCK_OBJECT = new Object();
//    private static final Object LOCK_OBJECT_2 = new Object();
//    private static int TEST_COUNTER = 0;

    private void checkParaBlockIsValid(ParaBlock paraBlock) throws SQLException {
        // Do nothing!
        preinit();
        long ID = 0l;
        int stamp = 0;
        int maxHeight = 0;
        long creatorID = 0l;
        boolean hasBlock = false;
        try(PreparedStatement statement = conn.prepareStatement("select id,stamp,creator_id from block where height=?")){
            statement.setLong(1, paraBlock.getHeight());
            try(ResultSet rs = statement.executeQuery()){
            while (rs.next()) {
                hasBlock = true;
                ID = rs.getLong(1);
                stamp = rs.getInt(2);
                creatorID = rs.getLong(3);
            }
            }
        }
        if (!hasBlock) {
            try(PreparedStatement statement = conn.prepareStatement("select max(height) from block")){
            try(ResultSet rs = statement.executeQuery()){
                while (rs.next()) {
                    maxHeight = rs.getInt(1);
                }
            }}
            if (maxHeight > 0) {
                if ((maxHeight+1) != paraBlock.getHeight()) {
                    rewriteWorkingDatabase(paraBlock.getHeight());
//                    commit();
                    System.out.println("=========== LOOOSE START (INTERNAL DETECTOR) =============");
                    return;
                }
            }
            return;
        }
        if (ID == paraBlock.getID() && stamp == paraBlock.getStamp() && creatorID == paraBlock.getGeneratorID()) {
            return;
        }
        rewriteWorkingDatabase(paraBlock.getHeight());
//        commit();
        System.out.println("=========== LOOOSE START =============");
    }

    private boolean checkParaBlockIsAccepted(ParaBlock paraBlock) throws SQLException {
        // Do nothing!
        boolean accepted = false;
        try (PreparedStatement statement = conn.prepareStatement("select accepted from block where id=? and height=? and stamp=?")) {
            statement.setLong(1, paraBlock.getID());
            statement.setInt(2, paraBlock.getHeight());
            statement.setInt(3, paraBlock.getStamp());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    accepted = rs.getBoolean(1);
                }
            }
        }
        return accepted;
    }

    private boolean checkParaBlockIsAccepted1440(ParaBlock paraBlock) throws SQLException {
        // Do nothing!
        boolean accepted = false;
        try (PreparedStatement statement = conn.prepareStatement("select accepted from block_1440 where id=? and height=? and stamp=?")) {
            statement.setLong(1, paraBlock.getID());
            statement.setInt(2, paraBlock.getHeight());
            statement.setInt(3, paraBlock.getStamp());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    accepted = rs.getBoolean(1);
                }
            }
        }
        return accepted;
    }

    private class CheckInternal {

        private List<ParaBlock.Payout> payouts = new ArrayList<>();
        private boolean hasTransactions = false;

        public List<ParaBlock.Payout> getPayouts() {
            return payouts;
        }

        public void setPayouts(List<ParaBlock.Payout> payouts) {
            this.payouts = payouts;
        }

        public void setHasTransactions(boolean hasTransactions) {
            this.hasTransactions = hasTransactions;
        }

        public boolean isHasTransactions() {
            return hasTransactions;
        }
    }

    private CheckInternal checkInternal(ParaBlock paraBlock, boolean ordinary) throws ParaMiningException {
        // Do nothing!
        CheckInternal retvalue = new CheckInternal();
//        log(true, "Processing block: " + paraBlock.getHeight());
        boolean blockExists = false;
        boolean calculateParatax = false;
        
        if (paraBlock == null) {
            return retvalue;
        }
        if (paraBlock.getTransactions().isEmpty())
            return retvalue;
        if (paraBlock.getHeight() >= Constants.BEGIN_BLOCK_WITH_PARATAX) {
            calculateParatax = true;
        }
        try {
            if (ordinary ? checkParaBlockIsAccepted(paraBlock) : checkParaBlockIsAccepted1440(paraBlock)) {
                return retvalue;
            }
            List<ParaBlock.Payout> payret = new ArrayList<>();
            if (!paraBlock.getTransactions().isEmpty()) {
                retvalue.setHasTransactions(true);
            }
            if ((payret = ordinary ? insertBlock(paraBlock.getID(), paraBlock.getHeight(), paraBlock.getFee(), paraBlock.getStamp(), paraBlock.getGeneratorID(), false)
                    : insertBlock1440(paraBlock.getID(), paraBlock.getHeight(), paraBlock.getFee(), paraBlock.getStamp(), paraBlock.getGeneratorID(), false)) != null) {
                retvalue.setPayouts(payret);
                blockExists = true;
            }
            List<ParaBlock.Transaction> allTransactionsReverseSort = ParaBlock.sort(paraBlock.getTransactions());
            List<ParaBlock.Transaction> allTransactionsDirectSort = ParaBlock.reverse(allTransactionsReverseSort);
            HashMap<Long, ParaBlock.Transaction> transactionsOrdinary = new HashMap<>();
            List<ParaBlock.Transaction> transactionsParamining = new ArrayList<>();
            Set<Long> blocksForCheck = new HashSet<>();
            Set<Long> senders = new HashSet<>();
            HashMap<Long, ParaMetrics> metricsMap = new HashMap<>();
            boolean holdEnabled = paraBlock.getHeight() >= Constants.HOLD_ENABLE_HEIGHT;
            HashMap<Long, Long> diffs = new HashMap<>();
            HashMap<Long, Integer> stamps = new HashMap<>();
            HashMap<Long, Long> diffs1440 = new HashMap<>();
            HashMap<Long, Integer> stamps1440 = new HashMap<>();
            
            if (holdEnabled) {
                for (ParaBlock.Transaction tx : allTransactionsDirectSort) { // We need to know if an arbitrary account has an outgoing transaction in this block
                    if (!senders.contains(tx.getSender()) && tx.getSender() != Genesis.CREATOR_ID) {
                        senders.add(tx.getSender());
                    }
                }
            }
            

            ParaCalculator calculator = new ParaCalculator(ordinary ? getGenesisEmission() : getGenesisEmission1440());
            for (ParaBlock.Transaction tx : allTransactionsReverseSort) {
                switch (tx.getType()) {
                    case ORDINARY:
                        if (!blockExists) {
                            if (!transactionsOrdinary.containsKey(tx.getSender())) {
                                transactionsOrdinary.put(tx.getSender(), tx);
                            }
                            if (!transactionsOrdinary.containsKey(tx.getReceiver())) {
                                transactionsOrdinary.put(tx.getReceiver(), tx);
                            }
                        }
                        break;
                    case PARAMINING:
                        if (tx.getParaBlockID() == null && paraBlock.getHeight() > 0) {
                            throw new ParaMiningException("Parablock with wrong internal structure!");
                        }
                        transactionsParamining.add(tx);
                        break;
                }
            }
            if (!blockExists) {
                boolean hasCoreTransaction = false;
                for (Map.Entry<Long, ParaBlock.Transaction> item : transactionsOrdinary.entrySet()) {
                    if (paraBlock.getHeight() < Constants.BEGIN_BLOCK_TIMESTAMP_CALCULATION) { // OLD
                        if (paraBlock.getGeneratorID() == item.getKey() && paraBlock.getStamp() > item.getValue().getStamp()) {
                            ParaMetrics metrics = ordinary ? getMetricsForAccount(paraBlock.getGeneratorID(), paraBlock.getStamp(), calculateParatax, paraBlock.getHeight(), false)
                                    : getMetricsForAccount1440(paraBlock.getGeneratorID(), paraBlock.getStamp(), calculateParatax, paraBlock.getHeight(), false);
                            if (metrics.getPayout() < 0l) {
                                continue;
                            }
                            ParaBlock.Payout payout = new ParaBlock.Payout();
                            payout.setBlockID(paraBlock.getID());
                            payout.setAmount(metrics.getPayout());
                            payout.setParaTax(metrics.getParaTax());
                            payout.setHeight(paraBlock.getHeight());
                            payout.setToID(paraBlock.getGeneratorID());
                            if (calculator.add(paraBlock.getGeneratorID(), metrics.getPayout())) {
                                hasCoreTransaction = true;
                                retvalue.getPayouts().add(payout);
                                if (ordinary) {
                                    insertForce(paraBlock.getID(), null, metrics.getPayout(), paraBlock.getGeneratorID(), paraBlock.getHeight(), metrics.getParaTax());
                                } else {
                                    insertForce1440(paraBlock.getID(), null, metrics.getPayout(), paraBlock.getGeneratorID(), paraBlock.getHeight(), metrics.getParaTax());
                                }
                            }
                        } else {
                            if (paraBlock.getGeneratorID() == item.getKey() && paraBlock.getStamp() <= item.getValue().getStamp()) {
                                hasCoreTransaction = true;
                            }
                            ParaMetrics metrics = ordinary ? getMetricsForAccount(item.getKey(), item.getValue().getStamp(), calculateParatax, paraBlock.getHeight(), false)
                                    : getMetricsForAccount1440(item.getKey(), item.getValue().getStamp(), calculateParatax, paraBlock.getHeight(), false);
                            if (metrics.getPayout() < 0l) {
                                continue;
                            }
                            ParaBlock.Payout payout = new ParaBlock.Payout();
                            payout.setBlockID(paraBlock.getID());
                            payout.setTxID(item.getValue().getID());
                            payout.setAmount(metrics.getPayout());
                            payout.setParaTax(metrics.getParaTax());
                            payout.setHeight(paraBlock.getHeight());
                            payout.setToID(item.getKey());
                            if (calculator.add(item.getKey(), metrics.getPayout())) {
                                retvalue.getPayouts().add(payout);
                                if (ordinary) {
                                    insertForce(paraBlock.getID(), item.getValue().getID(), metrics.getPayout(), item.getKey(), paraBlock.getHeight(),metrics.getParaTax());
                                } else {
                                    insertForce1440(paraBlock.getID(), item.getValue().getID(), metrics.getPayout(), item.getKey(), paraBlock.getHeight(),metrics.getParaTax());
                                }
                            }
                        }
                    } else { // NEW
                        if (paraBlock.getGeneratorID() == item.getKey()) {
                            ParaMetrics metrics = ordinary ? getMetricsForAccount(paraBlock.getGeneratorID(), paraBlock.getStamp(), calculateParatax, paraBlock.getHeight(), holdEnabled)
                                    : getMetricsForAccount1440(paraBlock.getGeneratorID(), paraBlock.getStamp(), calculateParatax, paraBlock.getHeight(), holdEnabled);
                            if (holdEnabled) {
                                metricsMap.put(item.getKey(), metrics);
                                if (metrics.isOnHoldAtHeight(paraBlock.getHeight())) {
                                    if (!senders.contains(item.getKey()))
                                        continue;
                                }
                            }
                            if (metrics.getPayout() < 0l) {
                                continue;
                            }
                            ParaBlock.Payout payout = new ParaBlock.Payout();
                            payout.setBlockID(paraBlock.getID());
                            payout.setAmount(metrics.getPayout());
                            payout.setParaTax(metrics.getParaTax());
                            payout.setHeight(paraBlock.getHeight());
                            payout.setToID(paraBlock.getGeneratorID());
                            if (calculator.add(paraBlock.getGeneratorID(), metrics.getPayout())) {
                                hasCoreTransaction = true;
                                retvalue.getPayouts().add(payout);
                                if (ordinary) {
                                    insertForce(paraBlock.getID(), null, metrics.getPayout(), paraBlock.getGeneratorID(), paraBlock.getHeight(),metrics.getParaTax());
                                } else {
                                    insertForce1440(paraBlock.getID(), null, metrics.getPayout(), paraBlock.getGeneratorID(), paraBlock.getHeight(),metrics.getParaTax());
                                }
                            }
                        } else {
                            ParaMetrics metrics = ordinary ? getMetricsForAccount(item.getKey(), paraBlock.getStamp(), calculateParatax, paraBlock.getHeight(), holdEnabled)
                                    : getMetricsForAccount1440(item.getKey(), paraBlock.getStamp(), calculateParatax, paraBlock.getHeight(), holdEnabled);
                            if (holdEnabled) {
                                metricsMap.put(item.getKey(), metrics);
                                if (metrics.isOnHoldAtHeight(paraBlock.getHeight())) {
                                    if (!senders.contains(item.getKey()))
                                        continue;
                                }
                            }
                            if (metrics.getPayout() < 0l) {
                                continue;
                            }
                            ParaBlock.Payout payout = new ParaBlock.Payout();
                            payout.setBlockID(paraBlock.getID());
                            payout.setTxID(item.getValue().getID());
                            payout.setAmount(metrics.getPayout());
                            payout.setParaTax(metrics.getParaTax());
                            payout.setHeight(paraBlock.getHeight());
                            payout.setToID(item.getKey());
                            if (calculator.add(item.getKey(), metrics.getPayout())) {
                                retvalue.getPayouts().add(payout);
                                if (ordinary) {
                                    insertForce(paraBlock.getID(), item.getValue().getID(), metrics.getPayout(), item.getKey(), paraBlock.getHeight(), metrics.getParaTax());
                                } else {
                                    insertForce1440(paraBlock.getID(), item.getValue().getID(), metrics.getPayout(), item.getKey(), paraBlock.getHeight(), metrics.getParaTax());
                                }
                            }                            
                        }
                    }
                }
                if (!hasCoreTransaction && paraBlock.getFee() > 0l) {
                    ParaMetrics metrics = ordinary ? getMetricsForAccount(paraBlock.getGeneratorID(), paraBlock.getStamp(), calculateParatax, paraBlock.getHeight(), holdEnabled)
                            : getMetricsForAccount1440(paraBlock.getGeneratorID(), paraBlock.getStamp(), calculateParatax, paraBlock.getHeight(), holdEnabled);
                    if (holdEnabled && !metricsMap.containsKey(paraBlock.getGeneratorID())) {
                        metricsMap.put(paraBlock.getGeneratorID(), metrics);
                    }
                    if (metrics.getPayout() >= 0l && (!holdEnabled || !metrics.isOnHoldAtHeight(paraBlock.getHeight()) || senders.contains(paraBlock.getGeneratorID()))) {
                        ParaBlock.Payout payout = new ParaBlock.Payout();
                        payout.setBlockID(paraBlock.getID());
                        payout.setAmount(metrics.getPayout());
                        payout.setParaTax(metrics.getParaTax());
                        payout.setHeight(paraBlock.getHeight());
                        payout.setToID(paraBlock.getGeneratorID());
                        if (calculator.add(paraBlock.getGeneratorID(), metrics.getPayout())) {
                            retvalue.getPayouts().add(payout);
                            if (ordinary) {
                                insertForce(paraBlock.getID(), null, metrics.getPayout(), paraBlock.getGeneratorID(), paraBlock.getHeight(), metrics.getParaTax());
                            } else {
                                insertForce1440(paraBlock.getID(), null, metrics.getPayout(), paraBlock.getGeneratorID(), paraBlock.getHeight(), metrics.getParaTax());
                            }
                        }
                    }
                }

                for (ParaBlock.Transaction item : allTransactionsDirectSort) {
                    if (paraBlock.getHeight() < Constants.BEGIN_BLOCK_TIMESTAMP_CALCULATION) {
                        if (item.getType() == ParaBlock.Type.ORDINARY) { // OLD
                            if (ordinary) {
                                createNetwork(item.getReceiver(), item.getSender(), item.getStamp(), paraBlock.getHeight());
                                addDiff(item.getReceiver(), item.getAmount() + calculator.get(item.getReceiver()), item.getStamp(), diffs, stamps);
                                addDiff(item.getSender(), 0l - item.getAmount() + calculator.get(item.getSender()) - item.getFee(), item.getStamp(), diffs, stamps);
                            } else {
                                createNetwork1440(item.getReceiver(), item.getSender(), item.getStamp());
                                addDiff(item.getReceiver(), item.getAmount() + calculator.get(item.getReceiver()), item.getStamp(), diffs1440, stamps1440);
                                addDiff(item.getSender(), 0l - item.getAmount() + calculator.get(item.getSender()) - item.getFee(), item.getStamp(), diffs1440, stamps1440);
                            }
                        }
                    } else { // NEW
                        if (item.getType() == ParaBlock.Type.ORDINARY) {
                            if (ordinary) {
                                createNetwork(item.getReceiver(), item.getSender(), paraBlock.getStamp(), paraBlock.getHeight());
                                addDiff(item.getReceiver(), item.getAmount() + calculator.get(item.getReceiver()), paraBlock.getStamp(), diffs, stamps);
                                addDiff(item.getSender(), 0l - item.getAmount() + calculator.get(item.getSender()) - item.getFee(), paraBlock.getStamp(), diffs, stamps);
                            } else {
                                createNetwork1440(item.getReceiver(), item.getSender(), paraBlock.getStamp());
                                addDiff(item.getReceiver(), item.getAmount() + calculator.get(item.getReceiver()), paraBlock.getStamp(), diffs1440, stamps1440);
                                addDiff(item.getSender(), 0l - item.getAmount() + calculator.get(item.getSender()) - item.getFee(), paraBlock.getStamp(), diffs1440, stamps1440);
                            }
                        }                        
                    }
                }
                if (paraBlock.getFee() > 0l) {
                    if (ordinary) {
                        addDiff(paraBlock.getGeneratorID(), paraBlock.getFee() + calculator.get(paraBlock.getGeneratorID()), paraBlock.getStamp(), diffs, stamps);
                    } else {
                        addDiff(paraBlock.getGeneratorID(), paraBlock.getFee() + calculator.get(paraBlock.getGeneratorID()), paraBlock.getStamp(), diffs1440, stamps1440);
                    }
                }
                if (calculator.hasGenesisDiff()) {
                    if (ordinary) {
                        addDiff(Genesis.CREATOR_ID, calculator.getGenesisDiff(), paraBlock.getStamp(), diffs, stamps);
                    } else {
                        addDiff(Genesis.CREATOR_ID, calculator.getGenesisDiff(), paraBlock.getStamp(), diffs1440, stamps1440);
                    }
                }
            }
            List<ParaBlock.Transaction>  transactionsParaminingSorted = ParaBlock.reverse(ParaBlock.sort(transactionsParamining));
            for (ParaBlock.Transaction tx : transactionsParaminingSorted) {
                if (paraBlock.getHeight() == 0 || paraBlock.getID() == Genesis.GENESIS_BLOCK_ID) {
                    if (ordinary) {
                        createNetwork(tx.getReceiver(), tx.getSender(), tx.getStamp(), 0);
                        addDiff(tx.getReceiver(), tx.getAmount(), tx.getStamp(), diffs, stamps);
                        addDiff(tx.getSender(), 0l - tx.getAmount() - tx.getFee(), tx.getStamp(), diffs, stamps);
                    } else {
                        createNetwork1440(tx.getReceiver(), tx.getSender(), tx.getStamp());
                        addDiff(tx.getReceiver(), tx.getAmount(), tx.getStamp(), diffs1440, stamps1440);
                        addDiff(tx.getSender(), 0l - tx.getAmount() - tx.getFee(), tx.getStamp(), diffs1440, stamps1440);
                    }
                } else {
                    if (!blocksForCheck.contains(tx.getParaBlockID())) {
                        blocksForCheck.add(tx.getParaBlockID());
                    }
                    
                    // Bad transaction available HERE =====|
                    if (!ParaExcludes.check(tx, paraBlock.getHeight())) {
                    // ===================================[AUTOFUCK]=|
                        if (!(ordinary ? checkForce(tx) : checkForce1440(tx))) {
                            throw new ParaMiningException((paraBlock.getHeight() + ": Genesis transaction wrong: " + tx.getID() + " > " + tx.getReceiver()) + " : "+tx.getAmount()+" \n"+tx.toString(), paraBlock.getHeight());
                        }
                    }
                    
                }
            }
            
            if (ordinary) {
                for (Long account : diffs.keySet()) {
                    if (holdEnabled && account != Genesis.CREATOR_ID) {
                        boolean isOnHold = metricsMap.get(account) != null && metricsMap.get(account).isOnHoldAtHeight(paraBlock.getHeight());
                        boolean shouldTransferHold = (!isOnHold || senders.contains(account)) && metricsMap.get(account) != null && metricsMap.get(account).getHold() > 0;
                        if (shouldTransferHold) {
                            addDiff(account, metricsMap.get(account).getHold(), null, diffs, stamps);
                            updateHold(account, -metricsMap.get(account).getHold());
                            insertHoldTransfer(account, metricsMap.get(account).getHold(), paraBlock.getHeight());
                        }
                        if (isOnHold && !senders.contains(account)) {
                            updateHold(account, diffs.get(account));
                        } else {
                            update(account, diffs.get(account), stamps.get(account));
                        }
                    } else {
                        update(account, diffs.get(account), stamps.get(account));
                    }
                }
            }

            for (Long ID : blocksForCheck) {
                if (ID == null) {
                    continue;
                }
                if (ordinary) {
                    checkBlockIsSuccess(ID);
                } else {
                    checkBlockIsSuccess1440(ID);
                }
            }
            if (ordinary) {
                checkBlockIsSuccess(paraBlock.getID());
            } else {
                checkBlockIsSuccess1440(paraBlock.getID());
            }
            if (paraBlock.getHeight() >= Constants.HOLD_ENABLE_HEIGHT - Constants.HOLD_RANGE) {
                setLastForgedBlockHeight(paraBlock.getGeneratorID(), paraBlock.getHeight());
            }
//            int limit = 512 - retvalue.getPayouts().size();
//            List<ParaBlock.Payout> finishPayouts = ordinary ? getUnpayedParaTransactions(paraBlock.getHeight(), limit) : getUnpayedParaTransactions1440(paraBlock.getHeight(), limit);
//            retvalue.getPayouts().addAll(finishPayouts);
        } catch (Exception ex) {
            Logger.logErrorMessage(ex.getMessage(), ex); // More details on exception's source
            if (ex.getMessage().contains("Genesis transaction wrong") || ex.getMessage().contains("\"FORCE\"")) {
                try {
                    rewriteWorkingDatabase(BlockchainImpl.getInstance().getHeight());
                } catch (SQLException sqle) {
                    throw new ParaMiningException("Failed to resurrect PRIZM database: " + sqle.getMessage());
                }
            }
            throw new ParaMiningException(ex.getMessage());
        }
//        commit();
        return retvalue;
    }
    
    private boolean finalizeOrdinaryCheck(int heightIn) {
        // Do nothing!
        if (useOnlyNewRollbackAlgo)
            return false;
        int height1440 = -1;
        try {
            height1440 = getParamLast1440();
        } catch (SQLException ex) {}
        int height = heightIn - CACHE_SIZE;
        
        if (height < 0  || height <= height1440) {
            return false;
        }
        
        ParaBlock paraBlock = getBlockFromBlockchainWithNoTransactions(height);
        if (paraBlock == null) return false;
        if (paraBlock.hasNoTransactions()) {
            try {
                insertBlock1440(paraBlock.getID(), paraBlock.getHeight(), 0, paraBlock.getStamp(), paraBlock.getGeneratorID(), true);
            } catch (SQLException ex) {}
//            commit();
            return false;
        }
        try {
            // Do nothing!
            checkInternal(paraBlock, false);
            return true;
        } catch (ParaMiningException ex) {
        }
        return false;
    }

    private void ressurectDatabaseIfNeeded(int height) throws SQLException {
        boolean allRight = true;
        try (PreparedStatement statement = conn.prepareStatement("select id,stamp,height from block where height=?")) {
            statement.setInt(1, height - 1);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long ID = rs.getLong(1);
                    int stamp = rs.getInt(2);
                    int i = rs.getInt(3);
                    ParaBlock block = getBlockFromBlockchainWithNoTransactions(i);
                    if (block.getID() != ID || block.getStamp() != stamp) {
                        allRight = false;
                        break;
                    }
                    
                }
            }
        }
        
        if (allRight) return;
        System.out.println("[@AAZO@] PrizmCop Security Service: === POTENTIAL PROBLEM IN PARA-CHAIN: RESSURECT PARAMINING DATABASE AT BLOCK "+height);
        rewriteWorkingDatabase(height);
//        commit();
    }
    
//    private static final HashMap<Integer, ParaBlock> CACHE = new HashMap<>();
    @Override
    public List<ParaBlock.Payout> check(ParaBlock paraBlock, int height, ParaBlock paraBlockIncognito) throws ParaMiningException {
        try {
            // Do nothing!
            preinit();
            try {
                ressurectDatabaseIfNeeded(height);
            } catch (SQLException ex) {
                Logger.logErrorMessage(" +++ DATABASE INCONSISTENCY DETECTED +++");
                try {
                    Logger.logErrorMessage(" +++ RECONSTRUCTING DATABASE - DO NOT RESTART PRIZMCORE +++");
                    rewriteWorkingDatabase(height);
                    Logger.logErrorMessage("=== DATABASE IS POSSIBLY FIXED ===");
                } catch (SQLException sqle) {
                    Logger.logErrorMessage(" +++ DATABASE IS DEAD - RE-SYNCHRONIZATION INEVITABLE +++");
                    System.exit(1);
                }
            }

            boolean isOrdinaryChecked;
            synchronized (LOCK_OBJECT) {
                                
                if (height > 0 && !useOnlyNewRollbackAlgo) {
                    isOrdinaryChecked = finalizeOrdinaryCheck(height);
                    if (isOrdinaryChecked) {
//                        commit();
                    }
                }
                if (paraBlock != null && paraBlock.getTransactions() != null) {
                    for (ParaBlock.Transaction trx : paraBlock.getTransactions()) {
                        if (trx != null && trx.getAmount() < 0d) {
                            trx.setAmount(0 - trx.getAmount());
                        }
                    }
                }
//            synchronized (CACHE) {
//                CACHE.remove((height-CACHE_SIZE-10));
//                CACHE.put(height, paraBlock);
                if (paraBlock == null && paraBlockIncognito != null) {
                    try {
                        insertBlock(paraBlockIncognito.getID(), paraBlockIncognito.getHeight(), 0, paraBlockIncognito.getStamp(), paraBlockIncognito.getGeneratorID(), true);
//                        commit();
                    } catch (SQLException ex) {
                    }
                    return new ArrayList<>();
                }
//            }
                try {
                    checkParaBlockIsValid(paraBlock);
                    CheckInternal checkInternalRetval = checkInternal(paraBlock, true);
                    if (!useOnlyNewRollbackAlgo) {
                        if (paraBlock!=null && paraBlock.getHeight() == 0) {
                            checkInternal(paraBlock, false);
                        }
                        final Integer newRollbackHeight = getParameter(FAST_ROLLBACK_ENABLED_HEIGHT);
                        if (newRollbackHeight != null && newRollbackHeight <= height) {
                            log(true, "Switched to the new rollback algorithm", true);
                            useOnlyNewRollbackAlgo = true;
                            dropOldDatabases(height);
                        }
                    } else trimDerivedTables();
//                    commit();

                    // --- BLOCK TO CREATE AND CONTROL BASE TARGETS ---------------------------
                    if (paraBlock != null && height >= Constants.INITIAL_BASE_TARGET_TRANSITION_700_BEGINS) {
                        try {
                            insertBaseTargetIntoDatabase(height, paraBlock.getID());
                        } catch (SQLException ex) {
                            log(false, "Can't insert BASE_TARGET for " + height + " ("+paraBlock.getID()+")", true);
                        }
                    }
                    // ------------------------------------------------------------------------
                    
                    return checkInternalRetval.getPayouts();
                } catch (ParaMiningException ex) {
                    rollback();
                    if (ex.hasHeight()) {
                        System.out.println(" === MIRACLE EXCHANGE ===                   (height: " + ex.getHeight() + ")");
                        try {
                            rewriteWorkingDatabase(ex.getHeight());
                        } catch (SQLException exception) {
                        }
                    }
//                    commit();
                    throw ex;
                } catch (SQLException ex) {
                    rollback();
                    throw new ParaMiningException(ex.getMessage());
                }
            }
        } finally {
//            commit();
        }
    }

    @Override
    public boolean canReceive(ParaBlock.Transaction trx) {
        synchronized (LOCK_OBJECT) {
            preinit();
            // Do nothing!
            try {
                return checkAnnounceCanReceive(trx);
            } catch (SQLException ex) {
                return false;
            } finally {
//                commit();
            }
        }
    }

    @Override
    public ParaMetrics getMetrics(long accountID) {
        synchronized (LOCK_OBJECT) {
            preinit();
            // Do nothing!
            ParaMetrics metrics = new ParaMetrics();
            try {
                long genesisEmission = 0l;
                if (getCurrentParaHeight() >= Constants.BEGIN_BLOCK_WITH_PARATAX) {
                    genesisEmission = getGenesisEmission();
                }
                try (PreparedStatement statement = conn.prepareStatement("select amount, balance, last, hold, last_forged_block_height from para where id=?")) {
                    statement.setLong(1, accountID);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            metrics.setAmount(rs.getLong(1));
                            metrics.setBalance(rs.getLong(2));
                            metrics.setBeforeStamp(rs.getInt(3));
                            metrics.setAfterStamp(metrics.getBeforeStamp() + 1000);
                            metrics.setHold(rs.getLong(4));
                            metrics.setLastForgedBlockHeight(rs.getInt(5));
                            metrics.setAccountID(accountID);
                            int height = Prizm.getBlockchain().getHeight();
                            boolean isOnHold = height >= Constants.HOLD_ENABLE_HEIGHT && metrics.isOnHoldAtHeight(height);
                            if (height < Constants.ENABLE_COMPOUND_AND_2X_PARATAX) {
                                metrics.calculateOrdinaryInterest();
                            } else {
                                metrics.calculateCompoundInterest(isOnHold);
                            }
                        }
                    }
                }
                int currentParaHeight = getCurrentParaHeight();
                boolean isOnHold = currentParaHeight >= Constants.HOLD_ENABLE_HEIGHT && metrics.isOnHoldAtHeight(currentParaHeight);
                if (genesisEmission != 0l) ParaMetrics.setParataxPercent(metrics, currentParaHeight, genesisEmission, isOnHold);
                if (getCurrentParaHeight() < Constants.ENABLE_COMPOUND_AND_2X_PARATAX) {
                    metrics.calculateOrdinaryInterest();
                } else {
                    metrics.calculateCompoundInterest(isOnHold);
                }
            } catch (SQLException ex) {
            } finally {
//                commit();
            }
            return metrics;
        }
    }
    
    @Override
    public ParaMetrics getMetrics(long accountID, int tektstmp) {
        synchronized (LOCK_OBJECT) {
            preinit();
            // Do nothing!
            ParaMetrics metrics = new ParaMetrics();
            try {
                long genesisEmission = 0l;
                if (getCurrentParaHeight() >= Constants.BEGIN_BLOCK_WITH_PARATAX) {
                    genesisEmission = getGenesisEmission();
                }
                try (PreparedStatement statement = conn.prepareStatement("select amount, balance, last, hold, last_forged_block_height from para where id=?")) {
                    statement.setLong(1, accountID);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            metrics.setAmount(rs.getLong(1));
                            metrics.setBalance(rs.getLong(2));
                            metrics.setBeforeStamp(rs.getInt(3));
                            metrics.setAfterStamp(tektstmp);
                            metrics.setHold(rs.getLong(4));
                            metrics.setLastForgedBlockHeight(rs.getInt(5));
                            metrics.setAccountID(accountID);
                        }
                    }
                }
                int currentParaHeight = getCurrentParaHeight();
                boolean isOnHold = currentParaHeight >= Constants.HOLD_ENABLE_HEIGHT && metrics.isOnHoldAtHeight(currentParaHeight);
                if (genesisEmission != 0l) ParaMetrics.setParataxPercent(metrics, currentParaHeight, genesisEmission, isOnHold);
                if (getCurrentParaHeight() < Constants.ENABLE_COMPOUND_AND_2X_PARATAX) {
                    metrics.calculateOrdinaryInterest();
                } else {
                    metrics.calculateCompoundInterest(isOnHold);
                }
            } catch (SQLException ex) {
            } finally {
//                commit();
            }
            return metrics;
        }
    }
    
    @Override
    public boolean isZeroblockFixed() {
        if (!zeroblockFixed) {
            preinit();
            Integer fixed = getParameter(ZEROBLOCK_FIXED);
            if (fixed != null) {
                zeroblockFixed = true;
            }
        }
        return zeroblockFixed;
    }
    
    @Override
    public void zeroblockFixed() {
        preinit();
        setParameter(ZEROBLOCK_FIXED, 0);
//        commit();
    }

    @Override
    public void shutdown() {
//        commit();
    }

    @Override
    public Connection getConnection() {
        preinit();
        return conn;
    }

    @Override
    public long getFixedFee(long amount) {
        long fee;
        fee = (long) (amount * 0.005 <= 5 ? 5 : amount * 0.005);
        if(Constants.FEE_MAX_10 > BlockchainImpl.getInstance().getHeight()){
            return fee;
        }
        return (fee <= 1000 ? fee : 1000);
    }
    

    @Override
    public HashMap<Long, ParaMetrics> getMetricsPacketsOfId(HashMap<Long, String> mgens) {
        HashMap<Long, ParaMetrics> mapmetriks = new HashMap<>();
        if (mgens.isEmpty()) {
            return mapmetriks;
        }
        synchronized (LOCK_OBJECT) {
            init();
            for (Map.Entry<Long, String> _gm : mgens.entrySet()) {
                //generators.add(_gm.getKey());
                mapmetriks.put(_gm.getKey(), Prizm.para().getMetrics(_gm.getKey(), Prizm.getEpochTime()) );
            }
            
            return mapmetriks;
        }
    }

    public static String getLineOfQs(int num) {
        // Joiner and Iterables from the Guava library
        String n = "?";
        for (int i = 0; i < num - 1; i++) {
            n = n + ",?";
        }
        return n;
    }
    
    
}
