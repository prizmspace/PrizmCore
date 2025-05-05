/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package prizm;


import java.math.BigInteger;

/**
 *
 * @author zoi
 */
public class ParaMetrics  {


    public static long getParataxPercent(long genesisAmount) {
        long ams = (Math.abs(genesisAmount) * 100L) / Math.abs(ParaEngine.MAXIMUM_PARAMINING_AMOUNT);
        if (ams > Constants.MAX_PARATAX_PERCENT) return Constants.MAX_PARATAX_PERCENT;
        if (ams < 0) return 0;
        return ams;
    }

    public static void setParataxPercent(ParaMetrics paraMetrics, int height, long genesisAmount, boolean isOnHold) {
        paraMetrics.setGenesisEmission(genesisAmount);
        if (height >= Constants.ENABLE_COMPOUND_AND_2X_PARATAX) {
            long percent = getParataxPercent(genesisAmount) * 2;
            if (isOnHold) {
                if (percent > Constants.MAX_PARATAX_PERCENT_ON_HOLD) percent = Constants.MAX_PARATAX_PERCENT_ON_HOLD;
            } else {
                if (percent > Constants.MAX_PARATAX_PERCENT) percent = Constants.MAX_PARATAX_PERCENT;
            }
            paraMetrics.setParaTax(percent);
            return;
        }
        paraMetrics.setParaTax(getParataxPercent(genesisAmount));
    }

    public static long getPercentAmount(long amount, long percent) {
        if (isOverflow(amount, percent)) {
            BigInteger bigAmount = BigInteger.valueOf(amount);
            BigInteger bigPercent = BigInteger.valueOf(percent);
            try {
                return bigAmount.multiply(bigPercent).divide(BigInteger.valueOf(100L)).longValueExact();
            } catch (ArithmeticException e) { // if bigAmount > Long.MAX_VALUE
                return (Long.MAX_VALUE / 100L) * percent;
            }
        }
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
    private long genesisEmission = 0l;
    private int lastForgedBlockHeight = 0;
    private long hold = 0l;

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
    //990 000
        

        if (amount>=100000l         && amount<=999999l) multi = 2.18d;
        if (amount>=1000000l        && amount<=9999999l) multi = 2.36d;
        if (amount>=10000000l       && amount<=99999999l) multi = 2.77d;
        if (amount>=100000000l      && amount<=999999999l) multi = 3.05d;
        if (amount>=1000000000l     && amount<=9999999999l) multi = 3.36d;
        if (amount>=10000000000l    && amount<=99999999999l) multi = 3.88d; // nash
        if (amount>=100000000000l) multi = 4.37d;
    //1000 000 000 ,00    

        this.multiplier = (multi * percent) / 100d;
        double days = (afterStamp - beforeStamp) / ORDINARY_DIVIDER;
        payout = (long) Math.floor((double) balance * (days * this.multiplier));

        // ParaTax calculation
        if (paraTax > 0) {
            paraTaxAmount = getPercentAmount(payout, paraTax);
            payout = getAmountMinusPercent(payout, paraTax);
        }
        if (payout < 0) payout = 0;

        return true;
    }

    public void decreaseAmountByGenesis() {
        double mtx = ((double)Math.abs(genesisEmission)/(double)Math.abs(ParaEngine.MAXIMUM_PARAMINING_AMOUNT));
        if (mtx>1d) mtx = 1d;
        mtx = 1 - mtx;
        if (mtx < 0.1) mtx = 0.1;
        payout *= mtx;
    }

    public boolean calculateCompoundInterest(boolean isOnHold) {

        setParataxPercent(this, 0, this.genesisEmission, isOnHold);
        this.calculateOrdinaryInterest();
        long ordinaryPayout = this.payout;

        setParataxPercent(this, Constants.ENABLE_COMPOUND_AND_2X_PARATAX, this.genesisEmission, isOnHold);
        this.calculateCompoundInterestInternal();
        long compoundPayout = this.payout;

        if (ordinaryPayout < compoundPayout) {
            setParataxPercent(this, 0, this.genesisEmission, isOnHold);
            this.calculateOrdinaryInterest();
        }

        decreaseAmountByGenesis();

        if (payout > Constants.MAX_BALANCE_AFTER_PARAMINING_PAYOUT_NQT)
            this.payout = Constants.MAX_BALANCE_AFTER_PARAMINING_PAYOUT_NQT;

        if (payout > 0 && this.payout + this.balance > Constants.MAX_BALANCE_AFTER_PARAMINING_PAYOUT_NQT) {
            this.payout = Constants.MAX_BALANCE_AFTER_PARAMINING_PAYOUT_NQT - this.balance;
        }

        return true;
    }

    private boolean calculateCompoundInterestInternal() {
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
        if (payout < 0) payout = 0;

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
    
    public long getParaTaxAmount() {
        return paraTaxAmount;
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



    public void setAfterStamp(int afterStamp) {
        this.afterStamp = afterStamp;
    }

    public double getMultiplier() {
        return multiplier;
    }


    public long getParaTax() {
        return paraTax;
    }

    public void setParaTax(long paraTax) {
        this.paraTax = paraTax;
    }


    public int getLastForgedBlockHeight() {
        return lastForgedBlockHeight;
    }

    public void setLastForgedBlockHeight(int lastForgedBlockHeight) {
        this.lastForgedBlockHeight = lastForgedBlockHeight;
    }

    public long getHold() {
        return hold;
    }

    public void setHold(long hold) {
        this.hold = hold;
    }

    public boolean isOnHoldAtHeight(int height) {
        return getLastForgedBlockHeight() >= height - Constants.HOLD_RANGE 
                && balance >= Constants.HOLD_BALANCE_MIN 
                && balance <= Constants.HOLD_BALANCE_MAX; 
    }
    
    private void setGenesisEmission(long genesisEmission) {
        this.genesisEmission = genesisEmission;
    }

    @Override
    public String toString() {
        return "ParaMetrics{" + "balance=" + balance + ", amount=" + amount + ", payout=" + payout + ", beforeStamp=" + beforeStamp + ", afterStamp=" + afterStamp + ", multiplier=" + multiplier + ", paraTax=" + paraTax + ", paraTaxAmount=" + paraTaxAmount + '}';
    }


    public static boolean isOverflow( long a, long b) {
        if (a == 0 || b == 0)
            return false;
        long result = a * b;
        return a != result / b;
    }
    
    // forrorbots
    
    private long AccouuntID = 0l;
    public long getAccountID() {
        return this.AccouuntID;
    }

    public void setAccountID(long accountID) {
        this.AccouuntID = accountID;
    }

}
