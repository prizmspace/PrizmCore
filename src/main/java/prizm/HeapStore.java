/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package prizm;

public class HeapStore {
    private long basic;
    private long connected;
    private long level;
    private HeapStore parent = null;

    public HeapStore(long basic, long connected, long level) {
        this.basic = basic;
        this.connected = connected;
        this.level = level;
    }

    public HeapStore getParent() {
        return parent;
    }

    public void setParent(HeapStore parent) {
        this.parent = parent;
    }

    public long getBasic() {
        return basic;
    }

    public void setBasic(long basic) {
        this.basic = basic;
    }

    public long getConnected() {
        return connected;
    }

    public void setConnected(long connected) {
        this.connected = connected;
    }

    public long getLevel() {
        return level;
    }

    public void setLevel(long level) {
        this.level = level;
    }
}
