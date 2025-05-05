/** ****************************************************************************
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
 ***************************************************************************** */
package prizm;

import prizm.AccountLedger.LedgerEntry;
import prizm.AccountLedger.LedgerEvent;
import prizm.AccountLedger.LedgerHolding;
import prizm.crypto.Crypto;
import prizm.crypto.EncryptedData;
import prizm.db.DbClause;
import prizm.db.DbIterator;
import prizm.db.DbKey;
import prizm.db.DbUtils;
import prizm.db.DerivedDbTable;
import prizm.db.VersionedEntityDbTable;
import prizm.db.VersionedPersistentDbTable;
import prizm.util.Convert;
import prizm.util.Listener;
import prizm.util.Listeners;
import prizm.util.Logger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings({"UnusedDeclaration", "SuspiciousNameCombination"})
public final class Account {

    public enum Event {
        BALANCE, UNCONFIRMED_BALANCE, SET_PROPERTY, DELETE_PROPERTY
    }

    public static final class AccountInfo {

        private final long accountId;
        private final DbKey dbKey;
        private String name;
        private String description;

        private AccountInfo(long accountId, String name, String description) {
            this.accountId = accountId;
            this.dbKey = accountInfoDbKeyFactory.newKey(this.accountId);
            this.name = name;
            this.description = description;
        }

        private AccountInfo(ResultSet rs, DbKey dbKey) throws SQLException {
            this.accountId = rs.getLong("account_id");
            this.dbKey = dbKey;
            this.name = rs.getString("name");
            this.description = rs.getString("description");
        }

        private void save(Connection con) throws SQLException {
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO account_info "
                    + "(account_id, name, description, height, latest) "
                    + "KEY (account_id, height) VALUES (?, ?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, this.accountId);
                DbUtils.setString(pstmt, ++i, this.name);
                DbUtils.setString(pstmt, ++i, this.description);
                pstmt.setInt(++i, Prizm.getBlockchain().getHeight());
                pstmt.executeUpdate();
            }
        }

        public long getAccountId() {
            return accountId;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        private void save() {
            if (this.name != null || this.description != null) {
                accountInfoTable.insert(this);
            } else {
                accountInfoTable.delete(this);
            }
        }

    }

    public static final class PublicKey {

        private final long accountId;
        private final DbKey dbKey;
        private byte[] publicKey;
        private int height;

        private PublicKey(long accountId, byte[] publicKey) {
            this.accountId = accountId;
            this.dbKey = publicKeyDbKeyFactory.newKey(accountId);
            this.publicKey = publicKey;
            this.height = Prizm.getBlockchain().getHeight();
        }

        private PublicKey(ResultSet rs, DbKey dbKey) throws SQLException {
            this.accountId = rs.getLong("account_id");
            this.dbKey = dbKey;
            this.publicKey = rs.getBytes("public_key");
            this.height = rs.getInt("height");
        }

        private void save(Connection con) throws SQLException {
            height = Prizm.getBlockchain().getHeight();
            try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO public_key (account_id, public_key, height, latest) "
                    + "KEY (account_id, height) VALUES (?, ?, ?, TRUE)")) {
                int i = 0;
                pstmt.setLong(++i, accountId);
                DbUtils.setBytes(pstmt, ++i, publicKey);
                pstmt.setInt(++i, height);
                pstmt.executeUpdate();
            }
        }

        public long getAccountId() {
            return accountId;
        }

        public byte[] getPublicKey() {
            return publicKey;
        }

        public int getHeight() {
            return height;
        }

    }

    static class DoubleSpendingException extends RuntimeException {

        DoubleSpendingException(String message, long accountId, long confirmed, long unconfirmed) {
            super(message + " account: " + Long.toUnsignedString(accountId) + " confirmed: " + confirmed + " unconfirmed: " + unconfirmed);
        }

    }

    private static final DbKey.LongKeyFactory<Account> accountDbKeyFactory = new DbKey.LongKeyFactory<Account>("id") {

        @Override
        public DbKey newKey(Account account) {
            return account.dbKey == null ? newKey(account.id) : account.dbKey;
        }

        @Override
        public Account newEntity(DbKey dbKey) {
            return new Account(((DbKey.LongKey) dbKey).getId());
        }

    };

    private static final VersionedEntityDbTable<Account> accountTable = new VersionedEntityDbTable<Account>("account", accountDbKeyFactory) {

        @Override
        protected Account load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new Account(rs, dbKey);
        }

        @Override
        protected void save(Connection con, Account account) throws SQLException {
            account.save(con);
        }

    };

    private static final DbKey.LongKeyFactory<AccountInfo> accountInfoDbKeyFactory = new DbKey.LongKeyFactory<AccountInfo>("account_id") {

        @Override
        public DbKey newKey(AccountInfo accountInfo) {
            return accountInfo.dbKey;
        }

    };

    private static final VersionedEntityDbTable<AccountInfo> accountInfoTable = new VersionedEntityDbTable<AccountInfo>("account_info",
            accountInfoDbKeyFactory, "name,description") {

        @Override
        protected AccountInfo load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new AccountInfo(rs, dbKey);
        }

        @Override
        protected void save(Connection con, AccountInfo accountInfo) throws SQLException {
            accountInfo.save(con);
        }

    };

    private static final DbKey.LongKeyFactory<PublicKey> publicKeyDbKeyFactory = new DbKey.LongKeyFactory<PublicKey>("account_id") {

        @Override
        public DbKey newKey(PublicKey publicKey) {
            return publicKey.dbKey;
        }

        @Override
        public PublicKey newEntity(DbKey dbKey) {
            return new PublicKey(((DbKey.LongKey) dbKey).getId(), null);
        }

    };

    private static final VersionedPersistentDbTable<PublicKey> publicKeyTable = new VersionedPersistentDbTable<PublicKey>("public_key", publicKeyDbKeyFactory) {

        @Override
        protected PublicKey load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new PublicKey(rs, dbKey);
        }

        @Override
        protected void save(Connection con, PublicKey publicKey) throws SQLException {
            publicKey.save(con);
        }

    };

    private static final DerivedDbTable accountGuaranteedBalanceTable = new DerivedDbTable("account_guaranteed_balance") {

        @Override
        public void trim(int height) {
            try (Connection con = Db.db.getConnection();
                    PreparedStatement pstmtDelete = con.prepareStatement("DELETE FROM account_guaranteed_balance "
                            + "WHERE height < ? AND height >= 0 LIMIT " + Constants.BATCH_COMMIT_SIZE)) {
                pstmtDelete.setInt(1, height - Constants.GUARANTEED_BALANCE_CONFIRMATIONS);
                int count;
                do {
                    count = pstmtDelete.executeUpdate();
                    Db.db.commitTransaction();
                } while (count >= Constants.BATCH_COMMIT_SIZE);
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }

    };

    private static final ConcurrentMap<DbKey, byte[]> publicKeyCache = Prizm.getBooleanProperty("prizm.enablePublicKeyCache") ?
            new ConcurrentHashMap<>() : null;

    private static final Listeners<Account, Event> listeners = new Listeners<>();

    public static boolean addListener(Listener<Account> listener, Event eventType) {
        return listeners.addListener(listener, eventType);
    }

    public static boolean removeListener(Listener<Account> listener, Event eventType) {
        return listeners.removeListener(listener, eventType);
    }

    public static int getCount() {
        return publicKeyTable.getCount();
    }

    public static Account getAccount(long id) {
        DbKey dbKey = accountDbKeyFactory.newKey(id);
        Account account = accountTable.get(dbKey);
        if (account == null) {
            PublicKey publicKey = publicKeyTable.get(dbKey);
            if (publicKey != null) {
                account = accountTable.newEntity(dbKey);
                account.publicKey = publicKey;
            }
        }
        return account;
    }

    public static Account getAccount(long id, int height) {
        DbKey dbKey = accountDbKeyFactory.newKey(id);
        Account account = accountTable.get(dbKey, height);
        if (account == null) {
            PublicKey publicKey = publicKeyTable.get(dbKey, height);
            if (publicKey != null) {
                account = accountTable.newEntity(dbKey);
                account.publicKey = publicKey;
            }
        }
        return account;
    }

    public static Account getAccount(byte[] publicKey) {
        long accountId = getId(publicKey);
        Account account = getAccount(accountId);
        if (account == null) {
            return null;
        }
        if (account.publicKey == null) {
            account.publicKey = publicKeyTable.get(accountDbKeyFactory.newKey(account));
        }
        if (account.publicKey == null || account.publicKey.publicKey == null || Arrays.equals(account.publicKey.publicKey, publicKey)) {
            return account;
        }
        throw new RuntimeException("DUPLICATE KEY for account " + Long.toUnsignedString(accountId)
                + " existing key " + Convert.toHexString(account.publicKey.publicKey) + " new key " + Convert.toHexString(publicKey));
    }

    public static long getId(byte[] publicKey) {
        byte[] publicKeyHash = Crypto.sha256().digest(publicKey);
        return Convert.fullHashToId(publicKeyHash);
    }

    public static byte[] getPublicKey(long id) {
        DbKey dbKey = publicKeyDbKeyFactory.newKey(id);
        byte[] key = null;
        if (publicKeyCache != null) {
            key = publicKeyCache.get(dbKey);
        }
        if (key == null) {
            PublicKey publicKey = publicKeyTable.get(dbKey);
            if (publicKey == null || (key = publicKey.publicKey) == null) {
                return null;
            }
            if (publicKeyCache != null) {
                publicKeyCache.put(dbKey, key);
            }
        }
        return key;
    }

    static Account addOrGetAccount(long id) {
        if (id == 0) {
            throw new IllegalArgumentException("Invalid accountId 0");
        }
        DbKey dbKey = accountDbKeyFactory.newKey(id);
        Account account = accountTable.get(dbKey);
        if (account == null) {
            account = accountTable.newEntity(dbKey);
            PublicKey publicKey = publicKeyTable.get(dbKey);
            if (publicKey == null) {
                publicKey = publicKeyTable.newEntity(dbKey);
                publicKeyTable.insert(publicKey);
            }
            account.publicKey = publicKey;
        }
        return account;
    }

    public static DbIterator<AccountInfo> searchAccounts(String query, int from, int to) {
        return accountInfoTable.search(query, DbClause.EMPTY_CLAUSE, from, to);
    }

    static {
        if (publicKeyCache != null) {
            Prizm.getBlockchainProcessor().addListener(block -> {
                publicKeyCache.remove(accountDbKeyFactory.newKey(block.getGeneratorId()));
                block.getTransactions().forEach(transaction -> {
                    publicKeyCache.remove(accountDbKeyFactory.newKey(transaction.getSenderId()));
                    if (!transaction.getAppendages(appendix -> (appendix instanceof Appendix.PublicKeyAnnouncement), false).isEmpty()) {
                        publicKeyCache.remove(accountDbKeyFactory.newKey(transaction.getRecipientId()));
                    }
                });
            }, BlockchainProcessor.Event.BLOCK_POPPED);

            Prizm.getBlockchainProcessor().addListener(block -> publicKeyCache.clear(), BlockchainProcessor.Event.RESCAN_BEGIN);

        }

    }

    static void init() {
    }

    private final long id;
    private final DbKey dbKey;
    private PublicKey publicKey;
    private long balanceNQT;
    private long unconfirmedBalanceNQT;
    private long forgedBalanceNQT;

    private Account(long id) {
        if (id != Crypto.rsDecode(Crypto.rsEncode(id))) {
            Logger.logMessage("CRITICAL ERROR: Reed-Solomon encoding fails for " + id);
        }
        this.id = id;
        this.dbKey = accountDbKeyFactory.newKey(this.id);
    }

    private Account(ResultSet rs, DbKey dbKey) throws SQLException {
        this.id = rs.getLong("id");
        this.dbKey = dbKey;
        this.balanceNQT = rs.getLong("balance");
        this.unconfirmedBalanceNQT = rs.getLong("unconfirmed_balance");
        this.forgedBalanceNQT = rs.getLong("forged_balance");
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO account (id, "
                + "balance, unconfirmed_balance, forged_balance, "
                + "active_lessee_id, height, latest) "
                + "KEY (id, height) VALUES (?, ?, ?, ?, ?, ?, TRUE)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.balanceNQT);
            pstmt.setLong(++i, this.unconfirmedBalanceNQT);
            pstmt.setLong(++i, this.forgedBalanceNQT);
            DbUtils.setLongZeroToNull(pstmt, ++i, 0L);
            pstmt.setInt(++i, Prizm.getBlockchain().getHeight());
            pstmt.executeUpdate();
        }
    }

    private void save() {
        if (balanceNQT == 0 && unconfirmedBalanceNQT == 0 && forgedBalanceNQT == 0) {
            accountTable.delete(this, true);
        } else {
            accountTable.insert(this);
        }
    }

    public long getId() {
        return id;
    }

    public AccountInfo getAccountInfo() {
        return accountInfoTable.get(accountDbKeyFactory.newKey(this));
    }

    void setAccountInfo(String name, String description) {
        name = Convert.emptyToNull(name.trim());
        description = Convert.emptyToNull(description.trim());
        AccountInfo accountInfo = getAccountInfo();
        if (accountInfo == null) {
            accountInfo = new AccountInfo(id, name, description);
        } else {
            accountInfo.name = name;
            accountInfo.description = description;
        }
        accountInfo.save();
    }

    public EncryptedData encryptTo(byte[] data, String senderSecretPhrase, boolean compress) {
        byte[] key = getPublicKey(this.id);
        if (key == null) {
            throw new IllegalArgumentException("Recipient account doesn't have a public key set");
        }
        return Account.encryptTo(key, data, senderSecretPhrase, compress);
    }

    public static EncryptedData encryptTo(byte[] publicKey, byte[] data, String senderSecretPhrase, boolean compress) {
        if (compress && data.length > 0) {
            data = Convert.compress(data);
        }
        return EncryptedData.encrypt(data, senderSecretPhrase, publicKey);
    }

    public byte[] decryptFrom(EncryptedData encryptedData, String recipientSecretPhrase, boolean uncompress) {
        byte[] key = getPublicKey(this.id);
        if (key == null) {
            throw new IllegalArgumentException("Sender account doesn't have a public key set");
        }
        return Account.decryptFrom(key, encryptedData, recipientSecretPhrase, uncompress);
    }

    public static byte[] decryptFrom(byte[] publicKey, EncryptedData encryptedData, String recipientSecretPhrase, boolean uncompress) {
        byte[] decrypted = encryptedData.decrypt(recipientSecretPhrase, publicKey);
        if (uncompress && decrypted.length > 0) {
            decrypted = Convert.uncompress(decrypted);
        }
        return decrypted;
    }

    public long getBalanceNQT() {
        return balanceNQT;
    }

    public long getUnconfirmedBalanceNQT() {
        return unconfirmedBalanceNQT;
    }

    public long getForgedBalanceNQT() {
        return forgedBalanceNQT;
    }

    public long getEffectiveBalancePrizm() {
        return getEffectiveBalancePrizm(Prizm.getBlockchain().getHeight());
    }

    public long getEffectiveBalancePrizm(int height) {
        if (height >= Constants.FORBID_FORGING_WITH_YOUNG_PUBLIC_KEY) {
            if (this.publicKey == null) {
                this.publicKey = publicKeyTable.get(accountDbKeyFactory.newKey(this));
            }
            if (this.publicKey == null || this.publicKey.publicKey == null || this.publicKey.height == 0 || height - this.publicKey.height <= 1440) {
                return 0; // cfb: Accounts with the public key revealed less than 1440 blocks ago are not allowed to generate blocks
            }
        }
        if (height < Constants.TRANSPARENT_FORGING_BLOCK_3) {
            if (Arrays.binarySearch(Genesis.GENESIS_RECIPIENTS, id) >= 0) {
                return balanceNQT / Constants.ONE_PRIZM;
            }
            long receivedInLastBlock = 0;
            for (Transaction transaction : Prizm.getBlockchain().getBlockAtHeight(height).getTransactions()) {
                if (id == transaction.getRecipientId()) {
                    receivedInLastBlock += transaction.getAmountNQT();
                }
            }
            return (balanceNQT - receivedInLastBlock) / Constants.ONE_PRIZM;
        }
        Prizm.getBlockchain().readLock();
        try {
            long effectiveBalanceNQT = getGuaranteedBalanceNQT(Constants.GUARANTEED_BALANCE_CONFIRMATIONS, height);
            return (effectiveBalanceNQT < Constants.MIN_FORGING_BALANCE_NQT) ? 0 : effectiveBalanceNQT / Constants.ONE_PRIZM;
        } finally {
            Prizm.getBlockchain().readUnlock();
        }
    }

    public long getGuaranteedBalanceNQT() {
        return getGuaranteedBalanceNQT(Constants.GUARANTEED_BALANCE_CONFIRMATIONS, Prizm.getBlockchain().getHeight());
    }

    public long getGuaranteedBalanceNQT(final int numberOfConfirmations, final int currentHeight) {
        Prizm.getBlockchain().readLock();
        try {
            int height = currentHeight - numberOfConfirmations;
            if (height + Constants.GUARANTEED_BALANCE_CONFIRMATIONS < Prizm.getBlockchainProcessor().getMinRollbackHeight()
                    || height > Prizm.getBlockchain().getHeight()) {
                throw new IllegalArgumentException("Height " + height + " not available for guaranteed balance calculation");
            }
            try (Connection con = Db.db.getConnection();
                    PreparedStatement pstmt = con.prepareStatement("SELECT SUM (additions) AS additions "
                            + "FROM account_guaranteed_balance WHERE account_id = ? AND height > ? AND height <= ?")) {
                pstmt.setLong(1, this.id);
                pstmt.setInt(2, height);
                pstmt.setInt(3, currentHeight);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (!rs.next()) {
                        return balanceNQT;
                    }
                    return Math.max(Math.subtractExact(balanceNQT, rs.getLong("additions")), 0);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        } finally {
            Prizm.getBlockchain().readUnlock();
        }
    }

    static boolean setOrVerify(long accountId, byte[] key) {
        DbKey dbKey = publicKeyDbKeyFactory.newKey(accountId);
        PublicKey publicKey = publicKeyTable.get(dbKey);
        if (publicKey == null) {
            publicKey = publicKeyTable.newEntity(dbKey);
        }
        if (publicKey.publicKey == null) {
            publicKey.publicKey = key;
            publicKey.height = Prizm.getBlockchain().getHeight();
            return true;
        }
        return Arrays.equals(publicKey.publicKey, key);
    }

    void apply(byte[] key) {
        PublicKey publicKey = publicKeyTable.get(dbKey);
        if (publicKey == null) {
            publicKey = publicKeyTable.newEntity(dbKey);
        }
        if (publicKey.publicKey == null) {
            publicKey.publicKey = key;
            publicKeyTable.insert(publicKey);
        } else if (!Arrays.equals(publicKey.publicKey, key)) {
            throw new IllegalStateException("Public key mismatch");
        } else if (publicKey.height >= Prizm.getBlockchain().getHeight() - 1) {
            PublicKey dbPublicKey = publicKeyTable.get(dbKey, false);
            if (dbPublicKey == null || dbPublicKey.publicKey == null) {
                publicKeyTable.insert(publicKey);
            }
        }
        if (publicKeyCache != null) {
            publicKeyCache.put(dbKey, key);
        }
        this.publicKey = publicKey;
    }

    void addToBalanceNQT(LedgerEvent event, long eventId, long amountNQT) {
        addToBalanceNQT(event, eventId, amountNQT, 0);
    }

    void addToBalanceNQT(LedgerEvent event, long eventId, long amountNQT, long feeNQT) {
        if (amountNQT == 0 && feeNQT == 0) {
            return;
        }
        long totalAmountNQT = Math.addExact(amountNQT, feeNQT);
        this.balanceNQT = Math.addExact(this.balanceNQT, totalAmountNQT);
        addToGuaranteedBalanceNQT(totalAmountNQT);
        checkBalance(this.id, this.balanceNQT, this.unconfirmedBalanceNQT);
        save();
        listeners.notify(this, Event.BALANCE);
        if (AccountLedger.mustLogEntry(this.id, false)) {
            if (feeNQT != 0) {
                AccountLedger.logEntry(new LedgerEntry(LedgerEvent.TRANSACTION_FEE, eventId, this.id,
                        LedgerHolding.PRIZM_BALANCE, null, feeNQT, this.balanceNQT - amountNQT));
            }
            if (amountNQT != 0) {
                AccountLedger.logEntry(new LedgerEntry(event, eventId, this.id,
                        LedgerHolding.PRIZM_BALANCE, null, amountNQT, this.balanceNQT));
            }
        }
    }

    void addToUnconfirmedBalanceNQT(LedgerEvent event, long eventId, long amountNQT) {
        addToUnconfirmedBalanceNQT(event, eventId, amountNQT, 0);
    }

    void addToUnconfirmedBalanceNQT(LedgerEvent event, long eventId, long amountNQT, long feeNQT) {
        if (amountNQT == 0 && feeNQT == 0) {
            return;
        }
        long totalAmountNQT = Math.addExact(amountNQT, feeNQT);
        this.unconfirmedBalanceNQT = Math.addExact(this.unconfirmedBalanceNQT, totalAmountNQT);
        checkBalance(this.id, this.balanceNQT, this.unconfirmedBalanceNQT);
        save();
        listeners.notify(this, Event.UNCONFIRMED_BALANCE);
        if (event == null) {
            return;
        }
        if (AccountLedger.mustLogEntry(this.id, true)) {
            if (feeNQT != 0) {
                AccountLedger.logEntry(new LedgerEntry(LedgerEvent.TRANSACTION_FEE, eventId, this.id,
                        LedgerHolding.UNCONFIRMED_PRIZM_BALANCE, null, feeNQT, this.unconfirmedBalanceNQT - amountNQT));
            }
            if (amountNQT != 0) {
                AccountLedger.logEntry(new LedgerEntry(event, eventId, this.id,
                        LedgerHolding.UNCONFIRMED_PRIZM_BALANCE, null, amountNQT, this.unconfirmedBalanceNQT));
            }
        }
    }

    void addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent event, long eventId, long amountNQT) {
        addToBalanceAndUnconfirmedBalanceNQT(event, eventId, amountNQT, 0);
    }

    void addToBalanceAndUnconfirmedBalanceNQT(LedgerEvent event, long eventId, long amountNQT, long feeNQT) {
        if (amountNQT == 0 && feeNQT == 0) {
            return;
        }
        long totalAmountNQT = Math.addExact(amountNQT, feeNQT);
        this.balanceNQT = Math.addExact(this.balanceNQT, totalAmountNQT);
        this.unconfirmedBalanceNQT = Math.addExact(this.unconfirmedBalanceNQT, totalAmountNQT);
        addToGuaranteedBalanceNQT(totalAmountNQT);
        checkBalance(this.id, this.balanceNQT, this.unconfirmedBalanceNQT);
        save();
        listeners.notify(this, Event.BALANCE);
        listeners.notify(this, Event.UNCONFIRMED_BALANCE);
        if (AccountLedger.mustLogEntry(this.id, true)) {
            if (feeNQT != 0) {
                AccountLedger.logEntry(new LedgerEntry(LedgerEvent.TRANSACTION_FEE, eventId, this.id,
                        LedgerHolding.UNCONFIRMED_PRIZM_BALANCE, null, feeNQT, this.unconfirmedBalanceNQT - amountNQT));
            }
            if (amountNQT != 0) {
                AccountLedger.logEntry(new LedgerEntry(event, eventId, this.id,
                        LedgerHolding.UNCONFIRMED_PRIZM_BALANCE, null, amountNQT, this.unconfirmedBalanceNQT));
            }
        }
        if (AccountLedger.mustLogEntry(this.id, false)) {
            if (feeNQT != 0) {
                AccountLedger.logEntry(new LedgerEntry(LedgerEvent.TRANSACTION_FEE, eventId, this.id,
                        LedgerHolding.PRIZM_BALANCE, null, feeNQT, this.balanceNQT - amountNQT));
            }
            if (amountNQT != 0) {
                AccountLedger.logEntry(new LedgerEntry(event, eventId, this.id,
                        LedgerHolding.PRIZM_BALANCE, null, amountNQT, this.balanceNQT));
            }
        }
    }

    void addToForgedBalanceNQT(long amountNQT) {
        if (amountNQT == 0) {
            return;
        }
        this.forgedBalanceNQT = Math.addExact(this.forgedBalanceNQT, amountNQT);
        save();
    }

    private static void checkBalance(long accountId, long confirmed, long unconfirmed) {
        if (accountId == Genesis.CREATOR_ID) {
            return;
        }
        if (confirmed < 0) {
            throw new DoubleSpendingException("Negative balance or quantity: ", accountId, confirmed, unconfirmed);
        }
        if (unconfirmed < 0) {
            throw new DoubleSpendingException("Negative unconfirmed balance or quantity: ", accountId, confirmed, unconfirmed);
        }
        if (unconfirmed > confirmed) {
            throw new DoubleSpendingException("Unconfirmed exceeds confirmed balance or quantity: ", accountId, confirmed, unconfirmed);
        }
    }

    private void addToGuaranteedBalanceNQT(long amountNQT) {
        if (amountNQT <= 0) {
            return;
        }
        int blockchainHeight = Prizm.getBlockchain().getHeight();
        try (Connection con = Db.db.getConnection();
                PreparedStatement pstmtSelect = con.prepareStatement("SELECT additions FROM account_guaranteed_balance "
                        + "WHERE account_id = ? and height = ?");
                PreparedStatement pstmtUpdate = con.prepareStatement("MERGE INTO account_guaranteed_balance (account_id, "
                        + " additions, height) KEY (account_id, height) VALUES(?, ?, ?)")) {
            pstmtSelect.setLong(1, this.id);
            pstmtSelect.setInt(2, blockchainHeight);
            try (ResultSet rs = pstmtSelect.executeQuery()) {
                long additions = amountNQT;
                if (rs.next()) {
                    additions = Math.addExact(additions, rs.getLong("additions"));
                }
                pstmtUpdate.setLong(1, this.id);
                pstmtUpdate.setLong(2, additions);
                pstmtUpdate.setInt(3, blockchainHeight);
                pstmtUpdate.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    @Override
    public String toString() {
        return "Account " + Long.toUnsignedString(getId());
    }
}
