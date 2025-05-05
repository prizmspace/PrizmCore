/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package prizm;

import java.util.TreeMap;

public class ParaCalculator {
    
    private final TreeMap<Long, Long> elements = new TreeMap<>();
    private long genesisDiff = 0l;
    private long genesisBalance;

    public ParaCalculator(long genesisBalance) {
        this.genesisBalance = genesisBalance;
    }

    public boolean add(long ID, long amount) {
        synchronized (elements) {
            if (genesisBalance - amount < ParaEngine.MAXIMUM_PARAMINING_AMOUNT) return false;
            genesisBalance -= amount;
            if (elements.containsKey(ID)) {
                long box = elements.get(ID);
                elements.put(ID, box+amount);
            } else {
                elements.put(ID, amount);
            }
            genesisDiff -= amount;
            return true;
        }
    }
    
    public long get(long ID) {
        synchronized (elements) {
            if (!elements.containsKey(ID)) return 0l;
            return elements.remove(ID);
        }
    }

    public long getGenesisDiff() {
        return genesisDiff;
    }
    
    public boolean hasGenesisDiff() {
        return genesisDiff != 0l;
    }
}
