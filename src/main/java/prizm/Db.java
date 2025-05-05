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

import prizm.db.BasicDb;
import prizm.db.TransactionalDb;

public final class Db {
    
    public static final String PREFIX = Constants.isTestnet ? "prizm.testDb" : "prizm.db";
    public static final String PREFIX_PARA = Constants.isTestnet ? "prizm.testDbPara" : "prizm.dbPara";
    
//    public static final String PARA_DB_URL = Prizm.getStringProperty(PREFIX_PARA + "Url");  //"./prizm_db/paraprizm"), "DB_CLOSE_ON_EXIT=FALSE;MVCC=TRUE;MV_STORE=FALSE"
    public static final String PARA_DB_URL = String.format("jdbc:%s:%s;%s", "h2", Prizm.getDbDir("./prizm_db/paraprizm"), "DB_CLOSE_ON_EXIT=FALSE;MV_STORE=FALSE");
    public static final String PARA_DB_USERNAME = Prizm.getStringProperty(PREFIX_PARA + "Username");
    public static final String PARA_DB_PASSWORD = Prizm.getStringProperty(PREFIX_PARA + "Password");
//    public static final String PARA_DB_PARAMS = Prizm.getStringProperty(PREFIX_PARA + "Params");
    
    public static final TransactionalDb db = new TransactionalDb(new BasicDb.DbProperties()
            .maxCacheSize(Prizm.getIntProperty("prizm.dbCacheKB"))
            .dbUrl(Prizm.getStringProperty(PREFIX + "Url"))
            .dbType(Prizm.getStringProperty(PREFIX + "Type"))
            .dbDir(Prizm.getStringProperty(PREFIX + "Dir"))
            .dbParams(Prizm.getStringProperty(PREFIX + "Params"))
            .dbUsername(Prizm.getStringProperty(PREFIX + "Username"))
            .dbPassword(Prizm.getStringProperty(PREFIX + "Password", null, true))
            .maxConnections(Prizm.getIntProperty("prizm.maxDbConnections"))
            .loginTimeout(Prizm.getIntProperty("prizm.dbLoginTimeout"))
            .defaultLockTimeout(Prizm.getIntProperty("prizm.dbDefaultLockTimeout") * 1000)
            .maxMemoryRows(Prizm.getIntProperty("prizm.dbMaxMemoryRows"))
    );

    public static void init() {
        db.init(new PrizmDbVersion());
    }

    static void shutdown() {
        db.shutdown();
    }

    private Db() {} // never

}
