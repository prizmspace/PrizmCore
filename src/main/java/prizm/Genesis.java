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

public final class Genesis {

    public static final long GENESIS_BLOCK_ID = 7024118705028086222L;
    public static final long CREATOR_ID = 8562459348922351959L;
    public static final long GENESIS_BLOCK_AMOUNT = 6500000000L;
    public static final byte[] CREATOR_PUBLIC_KEY = {
        73, 26, 13, 55, 20, -93, 38, -93, -84, -77, -1, -23, 10, 97, 34, 10, 23, 
        -48, -71, 44, 116, 123, -128, 54, -30, -31, 21, -18, -1, 33, -35, 114
    };

    public static final long[] GENESIS_RECIPIENTS = {
            Long.parseUnsignedLong("18139276522642802575"),
            Long.parseUnsignedLong("13984306266700362248"),
            Long.parseUnsignedLong("1006774793115587414"),
            Long.parseUnsignedLong("11996825010324229534"),
            Long.parseUnsignedLong("18111209633212755741")
    };


    public static final int[] GENESIS_AMOUNTS = {
            13000000,
            13000000,
            13000000,
            13000000,
            13000000
    };

    public static final byte[][] GENESIS_SIGNATURES = {
            {-61, -122, -26, 75, 70, 69, 85, 16, -97, -62, 1, -107, 41, -66, 97, 10, -88, -100, -123, 80, 101, 70, 27, 87, 39, 38, -18, -52, 83, -81, -51, 5, -31, -115, -62, -69, -45, 117, 55, 19, 73, 23, 82, 74, 123, 126, -39, -106, -97, 83, 56, 1, -90, 121, 101, -66, 47, 109, -127, -83, 14, 120, -106, 81},
            {-78, 16, -26, 22, -118, 32, 14, -123, 74, 35, 42, -119, 104, -65, -117, -109, 73, 104, -26, 64, 34, -44, -101, -127, 24, -46, 13, 102, -73, -1, -79, 8, -43, -115, -30, 43, 107, -123, 84, 121, -69, 56, -33, -37, 33, 53, -99, -103, 40, -88, -94, -14, 24, 7, -120, 126, -82, -59, -90, -28, 119, 60, 36, -24},
            {-56, 42, -77, -23, 93, -91, -20, -13, -29, -77, -99, -22, 100, -75, 16, -107, 11, -75, -107, -76, 86, -65, 31, 48, -77, -81, -125, 29, -127, 33, -55, 8, 58, -65, -106, 118, -102, -57, 0, -96, 99, -108, -83, -112, -16, -82, 11, -124, 48, -115, 119, -10, 110, 39, -2, -7, 69, 12, 80, 103, -14, 47, -18, 54},
            {77, 17, 123, -64, -31, 125, 42, 37, 70, -95, 83, 25, -57, 101, 127, 32, -3, -3, -92, -115, 97, 59, 39, -29, -108, -7, -57, -118, 69, -120, 67, 0, -116, -7, 11, -50, -39, -66, 74, 31, -100, -40, 65, -15, -65, -36, 92, -118, -106, 0, 66, 95, 64, 86, -114, -115, 1, 56, -118, -24, 24, -111, -83, -86},
            {52, -102, 105, -58, -64, 16, -101, -13, 112, -97, -53, 42, -105, -10, 122, 17, -31, -52, 71, 96, -100, 75, 77, -7, 51, 103, -66, -102, -95, -68, 87, 0, 66, -20, -85, -108, 57, 33, 107, 109, 57, 118, -56, -51, -106, 69, 84, 63, 64, 45, -101, 56, 83, 34, 21, 120, 47, 23, -77, -30, -55, 51, 68, -88},
        };

    public static final byte[] GENESIS_BLOCK_SIGNATURE = new byte[]{
    	    42, 85, -41, -111, -13, -71, 21, -68, 70, 96, -58, 61, 60, -19, -1, 35, -93, 92, -9, 2, 82, 43, 23, 117, 62, -76, 19, 118, -99, 121, -64, 2, -102, -127, 127, -21, 25, 32, -10, 96, -120, -1, -84, -21, 22, -7, 45, 73, 41, 113, -18, 11, 86, 87, 75, 42, 73, 6, -25, 86, 40, 52, -96, 98
    };

    private Genesis() {} // never

}
