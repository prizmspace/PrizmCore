/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package prizm;

import java.io.Serializable;

/**
 *
 * @author zoi
 */
public class ParaMetrics implements Serializable {
    
    public static long getParataxPercent(long genesisAmount) {
        long ams = (Math.abs(genesisAmount) * 100L) / Math.abs(ParaEngine.MAXIMUM_PARAMINING_AMOUNT);
        if (ams > 99) return 99;
        if (ams < 0) return 0;
        return ams;
    }

    public static void setParataxPercent(ParaMetrics paraMetrics, int height, long genesisAmount) {
        if (height >= Constants.ENABLE_COMPOUND_AND_2X_PARATAX) {
            long percent = getParataxPercent(genesisAmount) * 2;
            if (percent > 99) percent = 99;
            paraMetrics.setParaTax(percent);
            return;
        }
        paraMetrics.setParaTax(getParataxPercent(genesisAmount));
    }

    public static long getPercentAmount(long amount, long percent) {
        return (amount * percent) / 100l;
    }

    public static long getAmountMinusPercent(long amount, long percent) {
        return amount - getPercentAmount(amount, percent);
    }    
    
    private static final double ORDINARY_DIVIDER = 86400d;
    private static final double COMPOUND_DIVIDER = 50d;
    
    private long balance = 0l;
    private long amount = 0l;
    private long payout = 0l;
    private int beforeStamp = 0;
    private int afterStamp = 0;
    private double multiplier = 0;
    private long paraTax = 0l;
    private long paraTaxAmount = 0l;
    
    public boolean calculateOrdinaryInterest() {
        double multi = 1d;
        double percent = 0d;
        if (balance>=100l && balance<=9999l) percent = 0.12d;
        if (balance>=10000l && balance<=99999l) percent = 0.14d;
        if (balance>=100000l && balance<=999999l) percent = 0.18d;
        if (balance>=1000000l && balance<=4999999l) percent = 0.21d;
        if (balance>=5000000l && balance<=9999999l) percent = 0.25d;
        if (balance>=10000000l && balance<=49999999l) percent = 0.28d;
        if (balance>=50000000l && balance<100000000l) percent = 0.33d;
        
        if (amount>=100000l         && amount<=999999l) multi = 2.18d;
        if (amount>=1000000l        && amount<=9999999l) multi = 2.36d;
        if (amount>=10000000l       && amount<=99999999l) multi = 2.77d;
        if (amount>=100000000l      && amount<=999999999l) multi = 3.05d;
        if (amount>=1000000000l     && amount<=9999999999l) multi = 3.36d;
        if (amount>=10000000000l    && amount<=99999999999l) multi = 3.88d;
        if (amount>=100000000000l) multi = 4.37d;
                
        this.multiplier = (multi * percent) / 100d;
        double days = (afterStamp - beforeStamp) / ORDINARY_DIVIDER;
        payout = (long) Math.floor((double) balance * (days * this.multiplier));

        // ParaTax calculation        
        if (paraTax > 0) {
            paraTaxAmount = getPercentAmount(payout, paraTax);
            payout = getAmountMinusPercent(payout, paraTax);
        }
        return true;
    }

    public boolean calculateCompoundInterest() {
        double multi = 1d;
        double percent = 0d;
        if (balance>=100l && balance<=9999l) percent = 0.12d;
        if (balance>=10000l && balance<=99999l) percent = 0.14d;
        if (balance>=100000l && balance<=999999l) percent = 0.18d;
        if (balance>=1000000l && balance<=4999999l) percent = 0.21d;
        if (balance>=5000000l && balance<=9999999l) percent = 0.25d;
        if (balance>=10000000l && balance<=49999999l) percent = 0.28d;
        if (balance>=50000000l && balance<100000000l) percent = 0.33d;
        
        if (amount>=100000l         && amount<=999999l) multi = 2.18d;
        if (amount>=1000000l        && amount<=9999999l) multi = 2.36d;
        if (amount>=10000000l       && amount<=99999999l) multi = 2.77d;
        if (amount>=100000000l      && amount<=999999999l) multi = 3.05d;
        if (amount>=1000000000l     && amount<=9999999999l) multi = 3.36d;
        if (amount>=10000000000l    && amount<=99999999999l) multi = 3.88d;
        if (amount>=100000000000l) multi = 4.37d;
                
        this.multiplier = ((multi * percent) / 100d) / (ORDINARY_DIVIDER/COMPOUND_DIVIDER);
        double periods = (afterStamp - beforeStamp) / COMPOUND_DIVIDER;
        payout = (long) (Math.floor((double) balance * Math.pow(1d + this.multiplier, periods)) - balance);
        
        // ParaTax calculation
        if (paraTax > 0) {
            paraTaxAmount = getPercentAmount(payout, paraTax);
            payout = getAmountMinusPercent(payout, paraTax);
        }
        return true;
    }

    public long getBalance() {
        return balance;
    }

    public void setBalance(long balance) {
        this.balance = balance;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public long getPayout() {
        return payout;
    }

    public int getBeforeStamp() {
        return beforeStamp;
    }

    public void setBeforeStamp(int beforeStamp) {
        this.beforeStamp = beforeStamp;
    }

    public int getAfterStamp() {
        return afterStamp;
    }

    public void setAfterStamp(int afterStamp) {
        this.afterStamp = afterStamp;
    }

    public double getMultiplier() {
        return multiplier;
    }

    public void setPayout(long payout) {
        this.payout = payout;
    }

    public void setMultiplier(double multiplier) {
        this.multiplier = multiplier;
    }

    public long getParaTax() {
        return paraTax;
    }

    public void setParaTax(long paraTax) {
        this.paraTax = paraTax;
    }

    public long getParaTaxAmount() {
        return paraTaxAmount;
    }

    public void setParaTaxAmount(long paraTaxAmount) {
        this.paraTaxAmount = paraTaxAmount;
    }
    
    public boolean isParaTaxed() {
        return paraTax > 0;
    }

    @Override
    public String toString() {
        return "ParaMetrics{" + "balance=" + balance + ", amount=" + amount + ", payout=" + payout + ", beforeStamp=" + beforeStamp + ", afterStamp=" + afterStamp + ", multiplier=" + multiplier + ", paraTax=" + paraTax + ", paraTaxAmount=" + paraTaxAmount + '}';
    }
}
