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

package prizm;

public enum HoldingType {

    PRIZM((byte)0) {

        @Override
        public long getBalance(Account account, long holdingId) {
            if (holdingId != 0) {
                throw new IllegalArgumentException("holdingId must be 0");
            }
            return account.getBalanceNQT();
        }

        @Override
        public long getUnconfirmedBalance(Account account, long holdingId) {
            if (holdingId != 0) {
                throw new IllegalArgumentException("holdingId must be 0");
            }
            return account.getUnconfirmedBalanceNQT();
        }

        @Override
        void addToBalance(Account account, AccountLedger.LedgerEvent event, long eventId, long holdingId, long amount) {
            if (holdingId != 0) {
                throw new IllegalArgumentException("holdingId must be 0");
            }
            account.addToBalanceNQT(event, eventId, amount);
        }

        @Override
        void addToUnconfirmedBalance(Account account, AccountLedger.LedgerEvent event, long eventId, long holdingId, long amount) {
            if (holdingId != 0) {
                throw new IllegalArgumentException("holdingId must be 0");
            }
            account.addToUnconfirmedBalanceNQT(event, eventId, amount);
        }

        @Override
        void addToBalanceAndUnconfirmedBalance(Account account, AccountLedger.LedgerEvent event, long eventId, long holdingId, long amount) {
            if (holdingId != 0) {
                throw new IllegalArgumentException("holdingId must be 0");
            }
            account.addToBalanceAndUnconfirmedBalanceNQT(event, eventId, amount);
        }

    };

    public static HoldingType get(byte code) {
        for (HoldingType holdingType : values()) {
            if (holdingType.getCode() == code) {
                return holdingType;
            }
        }
        throw new IllegalArgumentException("Invalid holdingType code: " + code);
    }

    private final byte code;

    HoldingType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public abstract long getBalance(Account account, long holdingId);

    public abstract long getUnconfirmedBalance(Account account, long holdingId);

    abstract void addToBalance(Account account, AccountLedger.LedgerEvent event, long eventId, long holdingId, long amount);

    abstract void addToUnconfirmedBalance(Account account, AccountLedger.LedgerEvent event, long eventId, long holdingId, long amount);

    abstract void addToBalanceAndUnconfirmedBalance(Account account, AccountLedger.LedgerEvent event, long eventId, long holdingId, long amount);

}
