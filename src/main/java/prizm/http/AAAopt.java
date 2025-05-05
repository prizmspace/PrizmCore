/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package prizm.http;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;
import prizm.peer.Peer;
import prizm.util.JSON;

/**
 *
 * @author du44
 */
public class AAAopt {
    
    public static long prinuditelnyintervaloprosa = 1000; // ms - 1sek
    static JSONStreamAware _error(String error) {
        JSONObject response = new JSONObject();
        response.put("errorCode", 11);
        response.put("errorDescription", error);
        return JSON.prepare(response);
    }

    // for get peers
    //Collection<? extends Peer> peers = active ? Peers.getActivePeers() : state != null ? Peers.getPeers(state) : Peers.getAllPeers();
    public static boolean zanyatGetPeers = false;
    public static boolean _active;
    public static Peer.State _state; 
    public static JSONObject _responseGetPeers;
    public static long _lasttimeGetPeers=System.currentTimeMillis();
    
    // for get block
    public static int _maxHMsizeMGB = 5000;
    public static LinkedHashMap<String, JSONObject> _mapgetblockH = new LinkedHashMap<>();
    public static LinkedHashMap<String, JSONObject> _mapgetblockI = new LinkedHashMap<>();
    public static boolean zanyatGetBlock = false;

    // for get blockchainTransactions 
    public static boolean zanyatGBT = false; 
    public static long _lasttimeGetBlockchainTransactions=System.currentTimeMillis();
    public static JSONObject _responseGetBlockchainTransactions;
    public static long _accountId;
    public static int _numberOfConfirmations;
    public static byte _type;
    public static byte _subtype;
    public static int _timestamp;
    public static boolean _withMessage;
    public static boolean _phasedOnly;
    public static boolean _nonPhasedOnly;
    public static int _firstIndex;
    public static int _lastIndex;
    public static boolean _includeExpiredPrunable;
    public static boolean _executedOnly;
    
    // getUnconfirmed transactions 
    public static boolean _zanyatGUT = false;
    public static JSONObject _responseGUT;
    
    //get account
    public static int _maxHMsizeAcc = 3000;
    public static LinkedHashMap<Long, JSONObject> _mapACC = new LinkedHashMap<>();
    public static Set<Long> _zanatsetACC = new HashSet<>();
    
    
    //get account ledger
    public static int _maxHMsizeALedg = 3000;
    public static LinkedHashMap<Long, JSONObject> _mapALedg = new LinkedHashMap<>();
    public static Set<Long> _zanatsetALedg = new HashSet<>();

}
