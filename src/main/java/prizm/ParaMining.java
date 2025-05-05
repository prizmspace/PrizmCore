/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package prizm;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;

/**
 *
 * @author zoi
 */
public interface ParaMining {
    public List<ParaBlock.Payout> check(ParaBlock paraBlock, int height, ParaBlock paraBlockIncognito) throws ParaMiningException;
    public boolean canReceive(ParaBlock.Transaction trx);
    public ParaMetrics getMetrics(long accountID);
    public ParaMetrics getMetricsForAccount(long accountID, int stamp, boolean setParaTax, int height, boolean holdEnabled) throws SQLException;
    public long getFixedFee(long amount);
    public void rollbackToBlock(int blockHeight);
    public void shutdown();
    public void init();
    public void popLastBlock();
    public Connection getConnection ();
    public long getBaseTarget(int height) throws Exception;
    public boolean isZeroblockFixed();
    public void zeroblockFixed();
    
    //for robot
    public HashMap<Long, ParaMetrics> getMetricsPacketsOfId(HashMap<Long, String> mgens) ;
    public ParaMetrics getMetrics(long accountID, int tektstmp);
    
}
