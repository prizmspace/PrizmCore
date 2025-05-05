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

package prizm.addons;

import prizm.Account;
import prizm.BlockchainProcessor;
import prizm.Prizm;
import prizm.util.Convert;
import prizm.util.Logger;

public final class Demo implements AddOn {

    @Override
    public void init() {
        Prizm.getBlockchainProcessor().addListener(block -> Logger.logInfoMessage("Block " + block.getStringId()
                + " has been forged by account " + Convert.rsAccount(block.getGeneratorId()) + " having effective balance of "
                + Account.getAccount(block.getGeneratorId()).getEffectiveBalancePrizm()),
                BlockchainProcessor.Event.BEFORE_BLOCK_APPLY);
    }

    @Override
    public void shutdown() {
        Logger.logInfoMessage("Goodbye!");
    }

}
