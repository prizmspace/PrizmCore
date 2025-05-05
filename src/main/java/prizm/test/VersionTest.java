package prizm.test;

import prizm.Prizm;
import prizm.util.Version;

public class VersionTest {
    
    static int errors = 0;
    
    public static void main(String...args) {
        System.out.println(" === Test 1");
        assert1("1.10.4.4", "1.10.4.5", false, true);
        assert1("1.10.4", "1.10.4.1", false, true);
        assert1("1.10.4.1", "1.10.4.1", false, false);
        assert1("1.2.3.4", "1.2", true, false);
        assert1("1", "1.2.3.4.5", false, true);
        assert1(null, null, false, false);
        assert1("1.10.4.6", null, true, false);

        System.out.println("\n === Test 2");
        assert2("1.10.4.5", true);
        assert2("1.10.4.6", true);
        assert2("1.10.5.20", true);
        assert2("1.10.4.4", false);
        assert2("1.10.4.3", false);
        assert2("1.10.3.45", false);
        assert2("1", false);
        assert2("2", true);
        assert2("1.11", true);
        assert2("1.9", false);
        assert2("1.10.10", true);
        assert2("1.9.99999", false, 0);
        assert2("1.10.4.6.1", true);
        assert2("1.10.4.4.1", false);
        assert2("1.10.4.5.1", true);
        assert2("1.3.5v.vr", false, 0);
        assert2("1.3.5,3", false);
        assert2("1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1", false, 0);
        assert2("1.1.1.1.1.1.1.1.1.1.1.1.1.1.1.1", false, 0);
        assert2("a", false, 0);
        assert2("1234.1234.1234", false, 0);
        assert2("1.34867971927412987.462", false, 0);
        assert2("1.10.5", true);
        assert2("1.10.6", true);
        assert2("1.11.0.0.0", true, 1011000000000L);
        assert2("1.0.949", false);
        assert2("2.0.0.0.6", true);
        assert2("987984984321985191321191598198", false, 0);
        assert2("987984984321985191321191598198987984984321985191321191598198987984984321985191321191598198987984984321985191321191598198987984984321985191321191598198", false, 0);
        assert2("999.999.999.999.999", true, 999999999999999L);
        assert2("1999.9.9.9.9", false, 0);
        assert2("9999", false, 0);
        assert2("999", true);

        long startTime = System.currentTimeMillis();
        System.out.println("\n === Performance Test");
        final int operationsCount = 10000000;
        System.out.println("(please wait, performing "+operationsCount+" iterations)");
        for (int i = 0; i < operationsCount; i++) {
            new Version("1.10.4.5");
        }
        startTime = System.currentTimeMillis() - startTime;
        System.out.println(operationsCount + " iterations took " + (startTime/1000d) + " seconds");
        System.out.println(((double)operationsCount/((double)startTime/1000d)) + " operations per second");

        System.out.println("\n\n === Result: " + (errors==0?"OK":(errors+" ERROR(S)")));
    }

    
    
    private static boolean isCompatiblePeerVersion(String otherVersion) {
        return Prizm.VERSION.equals(otherVersion) || !Version.MINIMAL_COMPATIBLE.isNewerThen(otherVersion);
    }
    
    private static void assert2(String otherVersion, boolean expectedCompatible) {
        assert2(otherVersion, expectedCompatible, -1L);
    }
    private static void assert2(String otherVersion, boolean expectedCompatible, long expectedLongValue) {
        boolean compatible = isCompatiblePeerVersion(otherVersion);
        boolean expectedValid = expectedCompatible == compatible;
        if (!expectedValid) {
            errors++;
            System.out.println(Version.MINIMAL_COMPATIBLE.toString());
            System.out.println(new Version(otherVersion));
        }
        if (expectedLongValue != -1 && new Version(otherVersion).getLongVersion()!=expectedLongValue) {
            errors++;
            System.out.println(new Version(otherVersion));
        }
        System.out.println(otherVersion + " is " + (compatible?"":"NOT ") + "compatible " + (expectedValid?"OK":"ERROR"));
                
    }
    
    private static void assert1(String version1, String version2, boolean expected1, boolean expected2) {
        Version v1 = new Version(version1);
        Version v2 = new Version(version2);
        boolean v1newer = v1.isNewerThen(v2);
        boolean v2newer = v2.isNewerThen(v1);
        if (v1newer!=expected1)
            errors++;
        if (v2newer!=expected2)
            errors++;
        System.out.println(version1 + " is "+(v1newer?"NEWER":"NOT NEWER")+" then " + version2 + " " + (v1newer==expected1?"OK":"ERROR"));
        System.out.println(version2 + " is "+(v2newer?"NEWER":"NOT NEWER")+" then " + version1 + " " + (v2newer==expected2?"OK":"ERROR"));
    }
    
}
