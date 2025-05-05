/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package prizm;

/**
 *
 * @author zoi
 */
public class ParaMiningException extends Exception {

    private int height = -1;
    
    public ParaMiningException(String message) {
        super(message);
    }

    public ParaMiningException(int height) {
        this.height = height;
    }
    
    public ParaMiningException(String message, int height) {
        super(message);
        this.height = height;
    }
    
    public ParaMiningException() {
    }

    public int getHeight() {
        return height;
    }
    
    public boolean hasHeight() {
        return height != -1;
    }

    public void setHeight(int height) {
        this.height = height;
    }
}
