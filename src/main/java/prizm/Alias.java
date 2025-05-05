/******************************************************************************
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
 ******************************************************************************/

package prizm;

import prizm.db.DbClause;
import prizm.db.DbIterator;
import prizm.db.DbKey;
import prizm.db.DbUtils;
import prizm.db.VersionedEntityDbTable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class Alias {

    private static final DbKey.LongKeyFactory<Alias> aliasDbKeyFactory = new DbKey.LongKeyFactory<Alias>("id") {

        @Override
        public DbKey newKey(Alias alias) {
            return alias.dbKey;
        }

    };

    private static final VersionedEntityDbTable<Alias> aliasTable = new VersionedEntityDbTable<Alias>("alias", aliasDbKeyFactory) {

        @Override
        protected Alias load(Connection con, ResultSet rs, DbKey dbKey) throws SQLException {
            return new Alias(rs, dbKey);
        }

        @Override
        protected void save(Connection con, Alias alias) throws SQLException {
            alias.save(con);
        }

        @Override
        protected String defaultSort() {
            return " ORDER BY alias_name_lower ";
        }

    };

    public static int getCount() {
        return aliasTable.getCount();
    }

    public static int getAccountAliasCount(long accountId) {
        return aliasTable.getCount(new DbClause.LongClause("account_id", accountId));
    }

    public static DbIterator<Alias> getAliasesByOwner(long accountId, int from, int to) {
        return aliasTable.getManyBy(new DbClause.LongClause("account_id", accountId), from, to);
    }

    public static Alias getAlias(String aliasName) {
        return aliasTable.getBy(new DbClause.StringClause("alias_name_lower", aliasName.toLowerCase()));
    }

    public static DbIterator<Alias> getAliasesLike(String aliasName, int from, int to) {
        return aliasTable.getManyBy(new DbClause.LikeClause("alias_name_lower", aliasName.toLowerCase()), from, to);
    }

    public static Alias getAlias(long id) {
        return aliasTable.get(aliasDbKeyFactory.newKey(id));
    }

    static void deleteAlias(final String aliasName) {
        final Alias alias = getAlias(aliasName);
        aliasTable.delete(alias);
    }

    static void addOrUpdateAlias(Transaction transaction, Attachment.MessagingAliasAssignment attachment) {
        Alias alias = getAlias(attachment.getAliasName());
        if (alias == null) {
            alias = new Alias(transaction, attachment);
        } else {
            alias.accountId = transaction.getSenderId();
            alias.aliasURI = attachment.getAliasURI();
            alias.timestamp = Prizm.getBlockchain().getLastBlockTimestamp();
        }
        aliasTable.insert(alias);
    }

    static void init() {}


    private long accountId;
    private final long id;
    private final DbKey dbKey;
    private final String aliasName;
    private String aliasURI;
    private int timestamp;

    private Alias(Transaction transaction, Attachment.MessagingAliasAssignment attachment) {
        this.id = transaction.getId();
        this.dbKey = aliasDbKeyFactory.newKey(this.id);
        this.accountId = transaction.getSenderId();
        this.aliasName = attachment.getAliasName();
        this.aliasURI = attachment.getAliasURI();
        this.timestamp = Prizm.getBlockchain().getLastBlockTimestamp();
    }

    private Alias(ResultSet rs, DbKey dbKey) throws SQLException {
        this.id = rs.getLong("id");
        this.dbKey = dbKey;
        this.accountId = rs.getLong("account_id");
        this.aliasName = rs.getString("alias_name");
        this.aliasURI = rs.getString("alias_uri");
        this.timestamp = rs.getInt("timestamp");
    }

    private void save(Connection con) throws SQLException {
        try (PreparedStatement pstmt = con.prepareStatement("INSERT INTO alias (id, account_id, alias_name, "
                + "alias_uri, timestamp, height) "
                + "VALUES (?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, this.id);
            pstmt.setLong(++i, this.accountId);
            pstmt.setString(++i, this.aliasName);
            pstmt.setString(++i, this.aliasURI);
            pstmt.setInt(++i, this.timestamp);
            pstmt.setInt(++i, Prizm.getBlockchain().getHeight());
            pstmt.executeUpdate();
        }
    }

    public long getId() {
        return id;
    }

    public String getAliasName() {
        return aliasName;
    }

    public String getAliasURI() {
        return aliasURI;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public long getAccountId() {
        return accountId;
    }

}
