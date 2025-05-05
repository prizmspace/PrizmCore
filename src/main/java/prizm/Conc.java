/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package prizm;

public class Conc {
    private String LEFT_LINE = "update para set amount=amount+? where id in (";
    private String IDs = "";
    private int cointer=0;
    
    public boolean add(long ID) {
        if (IDs.length()>0) {
            IDs+=",";
        }
        IDs+=(ID+"");
        if (++cointer > 100) return false;
        return true;
    }

    public String query() {
        return LEFT_LINE+IDs+");";
    }

    public Conc() {
    }
    
    public Conc(boolean is1440) {
        this();
        if (is1440) {
            LEFT_LINE = "update para_1440 set amount=amount+? where id in (";
        }
    }
}
