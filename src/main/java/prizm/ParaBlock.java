/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package prizm;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author zoi
 */
public class ParaBlock implements Serializable {
    public static enum Type implements Serializable {
        ORDINARY,
        PARAMINING
    }
    public static class Transaction implements Serializable, Comparable<Transaction> {
        private long ID = 0;
        private long sender = 0;
        private long receiver = 0;
        private long amount = 0;
        private long fee = 0;
        private int stamp = 0;
        private Long paraBlockID = null;
        private Long paraTxID = null;
        private ParaBlock.Type type =  Type.ORDINARY;

        public long getID() {
            return ID;
        }

        public void setID(long ID) {
            this.ID = ID;
        }

        public int getStamp() {
            return stamp;
        }

        public void setStamp(int stamp) {
            this.stamp = stamp;
        }

        public long getSender() {
            return sender;
        }

        public void setSender(long sender) {
            this.sender = sender;
        }

        public long getReceiver() {
            return receiver;
        }

        public void setReceiver(long receiver) {
            this.receiver = receiver;
        }

        public long getAmount() {
            return amount;
        }

        public void setAmount(long amount) {
            this.amount = amount;
        }

        public Type getType() {
            return type;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public long getFee() {
            return fee;
        }

        public void setFee(long fee) {
            this.fee = fee;
        }

        public Long getParaBlockID() {
            return paraBlockID;
        }

        public void setParaBlockID(Long paraBlockID) {
            this.paraBlockID = paraBlockID;
        }

        public Long getParaTxID() {
            return paraTxID;
        }

        public void setParaTxID(Long paraTxID) {
            this.paraTxID = paraTxID;
        }

        @Override
        public int compareTo(Transaction o) {
            if (o == null) return -1;
            if (this.stamp > o.stamp) return -1;
            if (this.stamp < o.stamp) return 1;
            if (this.stamp == o.stamp) {
                if (this.ID > o.ID) return -1;
                if (this.ID < o.ID) return 1;
            }
            return 0;
        }

        @Override
        public String toString() {
            return "Transaction{" + "ID=" + ID + ", sender=" + sender + ", receiver=" + receiver + ", amount=" + amount + ", fee=" + fee + ", stamp=" + stamp + ", paraBlockID=" + paraBlockID + ", paraTxID=" + paraTxID + ", type=" + type + '}';
        }
    }
    
    public static class Payout implements Serializable {
        private long blockID = 0;
        private Long txID = null;
        private int height = 0;
        private long amount = 0;
        private long toID = 0;
        private int last = 0;
        private long paraTax = 0l;

        public long getBlockID() {
            return blockID;
        }

        public void setBlockID(long blockID) {
            this.blockID = blockID;
        }

        public Long getTxID() {
            return txID;
        }

        public void setTxID(Long txID) {
            this.txID = txID;
        }

        public long getAmount() {
            return amount;
        }

        public void setAmount(long amount) {
            this.amount = amount;
        }

        public long getToID() {
            return toID;
        }

        public void setToID(long toID) {
            this.toID = toID;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public int getLast() { return last; }

        public void setLast(int last) { this.last = last; }

        public long getParaTax() {
            return paraTax;
        }

        public void setParaTax(long paraTax) {
            this.paraTax = paraTax;
        }

        @Override
        public String toString() {
            return "Payout{" + "blockID=" + blockID + ", txID=" + txID + ", height=" + height + ", amount=" + amount + ", toID=" + toID + ", last=" + last + ", paraTax=" + paraTax + '}';
        }
    }
    
    public static class ParaParams implements Serializable {
        private long blockID = 0;
        private Long blockTxID = null;
        private boolean valid = false;

        public long getBlockID() {
            return blockID;
        }

        public void setBlockID(long blockID) {
            this.blockID = blockID;
        }

        public Long getBlockTxID() {
            return blockTxID;
        }

        public void setBlockTxID(Long blockTxID) {
            this.blockTxID = blockTxID;
        }

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }
        
        
    }
    
    public static List<Transaction> sort(List<Transaction> transactions) {
        ArrayList<Transaction> forSort = new ArrayList<>(transactions);
        Collections.sort(forSort);
        return forSort;
    }
    
    public static List<Transaction> reverse(List<Transaction> transactions) {
        ArrayList<Transaction> forSort = new ArrayList<>(transactions);
        Collections.reverse(forSort);
        return forSort;
    }
    
    private long ID = 0;
    private long fee = 0;
    private int height = 0;
    private int stamp = 0;
    private long generatorID = 0;
    private boolean noTransactions = false;
    
    private final List<Transaction> transactions = new ArrayList<Transaction>();

    public long getID() {
        return ID;
    }

    public void setID(long ID) {
        this.ID = ID;
    }

    public long getFee() {
        return fee;
    }

    public void setFee(long fee) {
        this.fee = fee;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getStamp() {
        return stamp;
    }

    public void setStamp(int stamp) {
        this.stamp = stamp;
    }

    public long getGeneratorID() {
        return generatorID;
    }

    public void setGeneratorID(long generatorID) {
        this.generatorID = generatorID;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public boolean hasNoTransactions() {
        return noTransactions;
    }

    public void setNoTransactions(boolean noTransactions) {
        this.noTransactions = noTransactions;
    }
}