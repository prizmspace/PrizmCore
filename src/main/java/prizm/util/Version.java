package prizm.util;

import prizm.Prizm;

import java.util.Arrays;

/**
 * Version parsing utility
 */
public class Version {
    
    private static final long 
            MAX_STRING_VERSION_LEGNTH = 19,
            MAX_STRING_PART_VERSION_LENGTH = 3,
            MAX_STRING_PARTS_COUNT = 5;
    
    private static final char[] ALLOWED_ALPHABET = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.'};
   
    public static final Version THIS = new Version(Prizm.VERSION);
    public static final Version MINIMAL_COMPATIBLE = new Version(Prizm.MINIMAL_COMPATIBLE_VERSION);
    
    private final String stringVersion;
    private final int[] intArrVersion;
    private final long longVersion;
    
    public Version(String version) {
        this.stringVersion = secureStringVersion(version);
        this.intArrVersion = stringToIntArray(this.stringVersion);
        this.longVersion = intArrayToLong(this.intArrVersion);
    }
    
    private Version(int[] version) {
        this.intArrVersion = version;
        this.stringVersion = intArrayToString(version);
        this.longVersion = intArrayToLong(this.intArrVersion);
    }
    
    private String secureStringVersion(String stringVersion) {
        if (stringVersion == null || stringVersion.isEmpty()) {
            return "0";
        }
        if (stringVersion.length() > MAX_STRING_VERSION_LEGNTH) {
            return "0";
        }
        for (char c : stringVersion.toCharArray()) {
            boolean found = false;
            for (char reference : ALLOWED_ALPHABET) {
                if (reference == c) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return "0";
            }
        }
        return stringVersion;
    }

    public String getStringVersion() {
        return stringVersion;
    }

    public int[] getIntArrVersion() {
        return intArrVersion;
    }

    public long getLongVersion() {
        return longVersion;
    }

    public boolean isNewerThen(String otherVersion) {
        return isNewerThen(new Version(otherVersion));
    }

    public boolean isNewerThen(Version otherVersion) {
        return this.longVersion > otherVersion.longVersion;
    }

    /**
     * @deprecated 
     * @param version
     * @return
     */
    private boolean isNewerThenLegacy(Version version) {
        try {
            boolean isThisVersionNewer = false;
            if (this.intArrVersion.length < version.intArrVersion.length) {
                boolean areExistingDigitsEqual = true;
                for (int i = 0; i < this.intArrVersion.length; i++) {
                    areExistingDigitsEqual = areExistingDigitsEqual && this.intArrVersion[i] == version.intArrVersion[i];
                    System.err.println("Comparing " + this.intArrVersion[i] + " and " + version.intArrVersion[i]);
                    if (this.intArrVersion[i] > version.intArrVersion[i]) {
                        System.err.println(this.intArrVersion[i] + " > " + version.intArrVersion[i]);
                        isThisVersionNewer = true;
                    }
                }
                if (areExistingDigitsEqual) {
                    return false;
                }
                return isThisVersionNewer;
            }
            if (this.intArrVersion.length > version.intArrVersion.length) {
                boolean areExistingDigitsEqual = true;
                for (int i = 0; i < version.intArrVersion.length; i++) {
                    areExistingDigitsEqual = areExistingDigitsEqual && this.intArrVersion[i] == version.intArrVersion[i];
                    if (this.intArrVersion[i] > version.intArrVersion[i])
                        isThisVersionNewer = true;
                }
                if (areExistingDigitsEqual) {
                    return true;
                }
                return isThisVersionNewer;
            }
            for (int i = 0; i < version.intArrVersion.length; i++) {
                if (this.intArrVersion[i] > version.intArrVersion[i])
                    isThisVersionNewer = true;
            }
            return isThisVersionNewer;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    private static int[] stringToIntArray(String version) {
        try {
            String[] strArr = version.split("\\.");
            if (strArr.length > MAX_STRING_PARTS_COUNT) {
                return new int[]{0};
            }
            int[] intArrVersion = new int[strArr.length];
            for (int i = 0; i < strArr.length; i++) {
                if (strArr[i].length() > MAX_STRING_PART_VERSION_LENGTH || strArr[i].length() == 0) {
                    return new int[]{0};
                } else {
                    intArrVersion[i] = Integer.parseInt(strArr[i]);
                }
            }
            return intArrVersion;
        } catch (Throwable t) {
            t.printStackTrace();
            return new int[]{0};
        }
    }
    
    private static long intArrayToLong(int[] version) {
        long value = 0L;
        long multi = 1000000000000L;
        for (int i = 0; i < version.length; i++) {
            value += version[i] * multi;
            multi /= 1000;
        }
        return value;
    }
    
    private static String intArrayToString(int[] version) {
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < version.length; i++) {
                if (i > 0)
                    sb.append(".");
                sb.append(version[i]);
            }
            return sb.toString();
        } catch (Throwable t) {
            t.printStackTrace();
            return "0";
        }
    }

    @Override
    public String toString() {
        return "{string = \""+stringVersion+"\"; ints = "+ Arrays.toString(intArrVersion)+"; long = "+longVersion+"}";
    }
}
