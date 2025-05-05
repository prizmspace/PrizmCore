/** ****************************************************************************
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
 ***************************************************************************** */
package prizm.http;

import prizm.Block;
import prizm.Prizm;
import prizm.util.Convert;
import org.json.simple.JSONStreamAware;

import javax.servlet.http.HttpServletRequest;
import org.json.simple.JSONObject;
import static prizm.http.AAAopt._mapgetblockH;
import static prizm.http.AAAopt._mapgetblockI;
import static prizm.http.AAAopt._maxHMsizeMGB;
import static prizm.http.AAAopt.zanyatGetBlock;

import static prizm.http.JSONResponses.INCORRECT_BLOCK;
import static prizm.http.JSONResponses.INCORRECT_HEIGHT;
import static prizm.http.JSONResponses.INCORRECT_TIMESTAMP;
import static prizm.http.JSONResponses.UNKNOWN_BLOCK;

public final class GetBlock extends APIServlet.APIRequestHandler {

    static final GetBlock instance = new GetBlock();

    private GetBlock() {
        super(new APITag[]{APITag.BLOCKS}, "block", "height", "timestamp", "includeTransactions", "includeExecutedPhased");
    }

    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) {

        Block blockData;
        String blockValue = Convert.emptyToNull(req.getParameter("block"));
        String heightValue = Convert.emptyToNull(req.getParameter("height"));
        String timestampValue = Convert.emptyToNull(req.getParameter("timestamp"));
        boolean includeTransactions = "true".equalsIgnoreCase(req.getParameter("includeTransactions"));
        boolean includeExecutedPhased = "true".equalsIgnoreCase(req.getParameter("includeExecutedPhased"));

        if(zanyatGetBlock){
            if(_mapgetblockH.containsKey(heightValue)){
                return _mapgetblockH.get(heightValue); 
            }
            if(_mapgetblockI.containsKey(blockValue)){
                return _mapgetblockI.get(blockValue); 
            }
        }
        
        try {
            zanyatGetBlock = true;    
            if (blockValue != null) {
                try {
                    blockData = Prizm.getBlockchain().getBlock(Convert.parseUnsignedLong(blockValue));
                } catch (RuntimeException e) {
                    return INCORRECT_BLOCK;
                }
            } else if (heightValue != null) {
                try {
                    int height = Integer.parseInt(heightValue);
                    if (height < 0 || height > Prizm.getBlockchain().getHeight()) {
                        return INCORRECT_HEIGHT;
                    }
                    blockData = Prizm.getBlockchain().getBlockAtHeight(height);
                } catch (RuntimeException e) {
                    return INCORRECT_HEIGHT;
                }
            } else if (timestampValue != null) {
                try {
                    int timestamp = Integer.parseInt(timestampValue);
                    if (timestamp < 0) {
                        return INCORRECT_TIMESTAMP;
                    }
                    blockData = Prizm.getBlockchain().getLastBlock(timestamp);
                } catch (RuntimeException e) {
                    return INCORRECT_TIMESTAMP;
                }
            } else {
                blockData = Prizm.getBlockchain().getLastBlock();
            }
            if (blockData == null) {
                return UNKNOWN_BLOCK;
            }
            JSONObject _responseGetBlock = JSONData.block(blockData, includeTransactions, includeExecutedPhased);
            _mapgetblockH.put(heightValue, _responseGetBlock);
            _mapgetblockI.put(blockValue, _responseGetBlock);
            if (_mapgetblockH.size() > _maxHMsizeMGB) {
                _mapgetblockH.remove(_mapgetblockH.firstEntry().getKey());
            }
            if (_mapgetblockI.size() > _maxHMsizeMGB) {
                _mapgetblockI.remove(_mapgetblockI.firstEntry().getKey());
            }
            return _responseGetBlock;
        } finally {
            zanyatGetBlock = false;
        }
    }

}
