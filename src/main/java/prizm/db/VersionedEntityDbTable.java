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

package prizm.db;


import prizm.Constants;
import prizm.Prizm;
import prizm.util.Logger;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public abstract class VersionedEntityDbTable<T> extends EntityDbTable<T> {

    protected VersionedEntityDbTable(String table, DbKey.Factory<T> dbKeyFactory) {
        super(table, dbKeyFactory, true, null);
    }

    protected VersionedEntityDbTable(String table, DbKey.Factory<T> dbKeyFactory, String fullTextSearchColumns) {
        super(table, dbKeyFactory, true, fullTextSearchColumns);
    }

    public final boolean delete(T t) {
        return delete(t, false);
    }

    public final boolean delete(T t, boolean keepInCache) {
        if (t == null) {
            return false;
        }
        if (!db.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }
        DbKey dbKey = dbKeyFactory.newKey(t);
        try (Connection con = db.getConnection();
             PreparedStatement pstmtCount = con.prepareStatement("SELECT 1 FROM " + table
                     + dbKeyFactory.getPKClause() + " AND height < ? LIMIT 1")) {
            int i = dbKey.setPK(pstmtCount);
            pstmtCount.setInt(i, Prizm.getBlockchain().getHeight());
            try (ResultSet rs = pstmtCount.executeQuery()) {
                if (rs.next()) {
                    try (PreparedStatement pstmt = con.prepareStatement("UPDATE " + table
                            + " SET latest = FALSE " + dbKeyFactory.getPKClause() + " AND latest = TRUE LIMIT 1")) {
                        dbKey.setPK(pstmt);
                        pstmt.executeUpdate();
                        save(con, t);
                        pstmt.executeUpdate(); // delete after the save
                    }
                    return true;
                } else {
                    try (PreparedStatement pstmtDelete = con.prepareStatement("DELETE FROM " + table + dbKeyFactory.getPKClause())) {
                        dbKey.setPK(pstmtDelete);
                        return pstmtDelete.executeUpdate() > 0;
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        } finally {
            if (!keepInCache) {
                db.getCache(table).remove(dbKey);
            }
        }
    }

    static void rollback(final TransactionalDb db, final String table, final int height, final DbKey.Factory dbKeyFactory) {
        if (!db.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }
        try (Connection con = db.getConnection();
             PreparedStatement pstmtSelectToDelete = con.prepareStatement("SELECT DISTINCT " + dbKeyFactory.getPKColumns()
                     + " FROM " + table + " WHERE height > ?");
             PreparedStatement pstmtDelete = con.prepareStatement("DELETE FROM " + table
                     + " WHERE height > ?");
             PreparedStatement pstmtSetLatest = con.prepareStatement("UPDATE " + table
                     + " SET latest = TRUE " + dbKeyFactory.getPKClause() + " AND height ="
                     + " (SELECT MAX(height) FROM " + table + dbKeyFactory.getPKClause() + ")")) {
            pstmtSelectToDelete.setInt(1, height);
            List<DbKey> dbKeys = new ArrayList<>();
            try (ResultSet rs = pstmtSelectToDelete.executeQuery()) {
                while (rs.next()) {
                    dbKeys.add(dbKeyFactory.newKey(rs));
                }
            }
            /*
            if (dbKeys.size() > 0 && Logger.isDebugEnabled()) {
                Logger.logDebugMessage(String.format("rollback table %s found %d records to update to latest", table, dbKeys.size()));
            }
            */
            pstmtDelete.setInt(1, height);
            int deletedRecordsCount = pstmtDelete.executeUpdate();
            /*
            if (deletedRecordsCount > 0 && Logger.isDebugEnabled()) {
                Logger.logDebugMessage(String.format("rollback table %s deleting %d records", table, deletedRecordsCount));
            }
            */
            for (DbKey dbKey : dbKeys) {
                int i = 1;
                i = dbKey.setPK(pstmtSetLatest, i);
                i = dbKey.setPK(pstmtSetLatest, i);
                pstmtSetLatest.executeUpdate();
                //Db.getCache(table).remove(dbKey);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    static void trim(final TransactionalDb db, final String table, final int height, final DbKey.Factory dbKeyFactory) {
        if (!db.isInTransaction()) {
            throw new IllegalStateException("Not in transaction");
        }
        int deleted = 0;
        long startTime = System.currentTimeMillis();
        try (Connection con = db.getConnection()) {
            // Delete all NOT LATEST versions of every account that has LATEST version OUTSIDE the max rollback range
            try (PreparedStatement psPreviousFrame = con.prepareStatement("delete from "+table+" where " + dbKeyFactory.getPKColumns() + " in " +
                    "(select "+dbKeyFactory.getPKColumns()+" from "+table+" where latest=true and height < ? and height >= ?) and latest=false and height < ?")) {
                psPreviousFrame.setInt(1, height);
                psPreviousFrame.setInt(2, Math.max(height - 2 * Constants.TRIM_FREQUENCY, 0));
                psPreviousFrame.setInt(3, height);
                int cleanedup = psPreviousFrame.executeUpdate();
                db.commitTransaction();
                deleted += cleanedup;
            }
            // Delete all NOT LATEST versions of every account that has LATEST version INSIDE the max rollback range EXCLUDING the latest NOT LATEST version OUTSIDE the max rollback range
            try (PreparedStatement psCurrentFrame = con.prepareStatement("select " + dbKeyFactory.getPKColumns() + ",max(height) as height from " + table + " where " + dbKeyFactory.getPKColumns() + " in (select " + dbKeyFactory.getPKColumns() + " from " + table + " where latest=true and height >= ? group by " + dbKeyFactory.getPKColumns() + ") and latest=false and height < ? group by " + dbKeyFactory.getPKColumns())) {
                psCurrentFrame.setInt(1, height);
                psCurrentFrame.setInt(2, height);
                int cleanedup = 0;
                try (ResultSet rs = psCurrentFrame.executeQuery()) {
                    try (PreparedStatement psCurrentFrameCleanup = con.prepareStatement("delete from " + table + dbKeyFactory.getPKClause() + " and height<?")) {
                        while (rs.next()) {
                            psCurrentFrameCleanup.setLong(1, rs.getLong(1));
                            psCurrentFrameCleanup.setInt(2, rs.getInt(2));
                            psCurrentFrameCleanup.addBatch();
                            cleanedup++;
                        }
                        psCurrentFrameCleanup.executeBatch();
                        db.commitTransaction();
                    }
                }
                deleted += cleanedup;
            }
            // Delete all accounts with ZERO balance OUTSIDE the rollback range - we should not store them to protect DB from cheap overflowing attack
            if (table.equalsIgnoreCase("account")) {
                try (PreparedStatement pstmtSelectBankrods = con.prepareStatement("select id,max(height) from (select id,height from account where balance=0 and unconfirmed_balance=0 and forged_balance=0 and height>=? and height<? order by height desc) group by id order by 2 desc;")) {
                    pstmtSelectBankrods.setInt(1, Math.max(0, height - Constants.TRIM_FREQUENCY));
                    pstmtSelectBankrods.setInt(2, height);
                    try (ResultSet bankrods = pstmtSelectBankrods.executeQuery()) {
                        try (PreparedStatement pstmtDeleteBankrods = con.prepareStatement("delete from account where id=? and height<=?")) {
                            int trimmedBankrods = 0;
                            while (bankrods.next()) {
                                pstmtDeleteBankrods.clearParameters();
                                pstmtDeleteBankrods.setLong(1, bankrods.getLong(1));
                                pstmtDeleteBankrods.setInt(2, bankrods.getInt(2));
                                trimmedBankrods++;
                                pstmtDeleteBankrods.addBatch();
                            }
                            pstmtDeleteBankrods.executeBatch();
                            db.commitTransaction();
                            deleted += trimmedBankrods;
                        }
                    }

                }
            }
            double timeSpent = 0d;
            if (System.currentTimeMillis()-startTime != 0)
                timeSpent = (double)(System.currentTimeMillis()-startTime)/1000d;
            double speed = 0d;
            if (timeSpent != 0d)
                speed = (double)deleted / timeSpent;
            Logger.logDebugMessage("Table "+table.toUpperCase()+" trimmed for "+timeSpent+" seconds (trimmed "+deleted+" entries, "+speed+" per second)");
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

}
