/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package prizm;

import java.sql.Connection;
import java.util.List;

/**
 *
 * @author zoi
 */
public interface ParaMining {
    public List<ParaBlock.Payout> check(ParaBlock paraBlock, int height, ParaBlock paraBlockIncognito) throws ParaMiningException;
    public boolean canReceive(ParaBlock.Transaction trx);
    public ParaMetrics getMetrics(long accountID);
    public long getFixedFee(long amount);
    public void rollbackToBlock(int blockHeight);
    public void shutdown();
    public void init();
    public void popLastBlock();
    public Connection getConnection ();
}
