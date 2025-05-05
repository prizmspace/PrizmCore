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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public abstract class PrunableDbTable<T> extends PersistentDbTable<T> {

    protected PrunableDbTable(String table, DbKey.Factory<T> dbKeyFactory) {
        super(table, dbKeyFactory);
    }

    protected PrunableDbTable(String table, DbKey.Factory<T> dbKeyFactory, String fullTextSearchColumns) {
        super(table, dbKeyFactory, fullTextSearchColumns);
    }

    PrunableDbTable(String table, DbKey.Factory<T> dbKeyFactory, boolean multiversion, String fullTextSearchColumns) {
        super(table, dbKeyFactory, multiversion, fullTextSearchColumns);
    }

    @Override
    public final void trim(int height) {
        prune();
        super.trim(height);
    }

    protected void prune() {
        if (Constants.ENABLE_PRUNING) {
            try (Connection con = db.getConnection();
                 PreparedStatement pstmt = con.prepareStatement("DELETE FROM " + table + " WHERE transaction_timestamp < ? LIMIT " + Constants.BATCH_COMMIT_SIZE)) {
                pstmt.setInt(1, Prizm.getEpochTime() - Constants.MAX_PRUNABLE_LIFETIME);
                int deleted;
                do {
                    deleted = pstmt.executeUpdate();
                    if (deleted > 0) {
                        Logger.logDebugMessage("Deleted " + deleted + " expired prunable data from " + table);
                    }
                    db.commitTransaction();
                } while (deleted >= Constants.BATCH_COMMIT_SIZE);
            } catch (SQLException e) {
                throw new RuntimeException(e.toString(), e);
            }
        }
    }

}
