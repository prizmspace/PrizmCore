/******************************************************************************
 * Copyright © 2013-2016 The Nxt Core Developers.                             *
 *                                                                            *
 * See the AUTHORS.txt, DEVELOPER-AGREEMENT.txt and LICENSE.txt files at      *
 * the top-level directory of this distribution for the individual copyright  *
 * holder information and the developer policies on copyright and licensing.  *
 *                                                                            *
 * Unless otherwise agreed in a custom licensing agreement, no part of the    *
 * Nxt software, including this file, may be copied, modified, propagated,    *
 * or distributed except according to the terms contained in the LICENSE.txt  *
 * file.                                                                      *
 *                                                                            *
 * Removal or modification of this copyright notice is prohibited.            *
 *                                                                            *
 ******************************************************************************/

package prizm.http;

import prizm.peer.Peer;
import prizm.peer.Peers;
import prizm.util.Convert;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import static prizm.http.AAAopt._active;
import static prizm.http.AAAopt._lasttimeGetPeers;
import static prizm.http.AAAopt._state;
import static prizm.http.AAAopt.prinuditelnyintervaloprosa;
import static prizm.http.AAAopt._responseGetPeers;
import static prizm.http.AAAopt.zanyatGetPeers;

public final class GetPeers extends APIServlet.APIRequestHandler {

    static final GetPeers instance = new GetPeers();
    
    
    

    private GetPeers() {
        super(new APITag[] {APITag.NETWORK}, "active", "state", "service", "service", "service", "includePeerInfo");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {
        
        if(zanyatGetPeers && _responseGetPeers!=null){
            return _responseGetPeers;
        }
        try{
        zanyatGetPeers=true;    
        boolean active = "true".equalsIgnoreCase(req.getParameter("active"));
        String stateValue = Convert.emptyToNull(req.getParameter("state"));
        String[] serviceValues = req.getParameterValues("service");
        boolean includePeerInfo = "true".equalsIgnoreCase(req.getParameter("includePeerInfo"));
        Peer.State state;
        if (stateValue != null) {
            try {
                state = Peer.State.valueOf(stateValue);
            } catch (RuntimeException exc) {
                return JSONResponses.incorrect("state", "- '" + stateValue + "' is not defined");
            }
        } else {
            state = null;
        }
        if(System.currentTimeMillis()-prinuditelnyintervaloprosa>_lasttimeGetPeers && _responseGetPeers!=null &&_active==active && _state.equals(state)){
            return _responseGetPeers;
        }
        _active=active;
        _state=state;
        _lasttimeGetPeers=System.currentTimeMillis()-prinuditelnyintervaloprosa;
        
        long serviceCodes = 0;
        if (serviceValues != null) {
            for (String serviceValue : serviceValues) {
                try {
                    serviceCodes |= Peer.Service.valueOf(serviceValue).getCode();
                } catch (RuntimeException exc) {
                    return JSONResponses.incorrect("service", "- '" + serviceValue + "' is not defined");
                }
            }
        }

        Collection<? extends Peer> peers = active ? Peers.getActivePeers() : state != null ? Peers.getPeers(state) : Peers.getAllPeers();
        JSONArray peersJSON = new JSONArray();
        if (serviceCodes != 0) {
            final long services = serviceCodes;
            if (includePeerInfo) {
                peers.forEach(peer -> {
                    if (peer.providesServices(services)) {
                        peersJSON.add(JSONData.peer(peer));
                    }
                });
            } else {
                peers.forEach(peer -> {
                    if (peer.providesServices(services)) {
                        peersJSON.add(peer.getHost());
                    }
                });
            }
        } else {
            if (includePeerInfo) {
                peers.forEach(peer -> peersJSON.add(JSONData.peer(peer)));
            } else {
                peers.forEach(peer -> peersJSON.add(peer.getHost()));
            }
        }

        JSONObject response = new JSONObject();
        response.put("peers", peersJSON);
        _responseGetPeers=response;
        return response;
        }finally{
            zanyatGetPeers=false;
        }
    }

    @Override
    protected boolean allowRequiredBlockParameters() {
        return false;
    }

}
