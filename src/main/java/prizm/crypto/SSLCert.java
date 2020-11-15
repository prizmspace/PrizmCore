package prizm.crypto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;

import prizm.util.Convert;
import prizm.util.Logger;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

public class SSLCert {
    private int keysize = 1024;
    private String commonName = "PrizmCore";
    private String organizationalUnit = "Users";
    private String organization = "Prizm Community";
    private String city = "Prizm";
    private String state = "Prizm";
    private String country = "DE";
    private long validity = 4384; // 3 years
    private String alias = "prizm";
    private char[] keyPass;
    private String pathToSave;

    public SSLCert(String keyPass) {
        this.keyPass = keyPass.toCharArray();
    }

    public String write(String path) {
        try {
            final String fingerprint = writeInternal(path);
            return fingerprint;
        } catch (Exception ex) {
            return null;
        }
    }

    public boolean isValid(String path) {
        if (doesExist(path)) {
            if (tryRead(path)) {
                return true;
            } else {
                Logger.logInfoMessage("SSLEnforce: invalid certificate file, deleted");
                File f = new File(path);
                f.delete();
                return false;
            }
        } else return false;
    }

    private boolean doesExist(String path) {
        final File f = new File(path);
        return f.exists() && !f.isDirectory();
    }

    private boolean tryRead(String path) {
        try {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(new FileInputStream(path), keyPass);
            keyStore.getKey(alias, keyPass);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String writeInternal(String path) throws Exception {
        this.pathToSave = path;

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);

        CertAndKeyGen keypair = new CertAndKeyGen("RSA", "SHA1WithRSA", null);

        X500Name x500Name = new X500Name(commonName, organizationalUnit, organization, city, state, country);

        keypair.generate(keysize);
        PrivateKey privKey = keypair.getPrivateKey();

        X509Certificate[] chain = new X509Certificate[1];

        chain[0] = keypair.getSelfCertificate(x500Name, new Date(), (long) validity * 24 * 60 * 60);
        MessageDigest sha1 = MessageDigest.getInstance("SHA1");
        sha1.update(chain[0].getEncoded());

        String fingerprint = Convert.toHexString(sha1.digest()).toUpperCase().replaceAll("(.{2})", "$1 ");


        keyStore.setKeyEntry(alias, privKey, keyPass, chain);

        keyStore.store(new FileOutputStream(this.pathToSave), keyPass);
        return fingerprint;
    }

    public int getKeysize() {
        return keysize;
    }

    public void setKeysize(int keysize) {
        this.keysize = keysize;
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }

    public String getOrganizationalUnit() {
        return organizationalUnit;
    }

    public void setOrganizationalUnit(String organizationalUnit) {
        this.organizationalUnit = organizationalUnit;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public long getValidity() {
        return validity;
    }

    public void setValidity(long validity) {
        this.validity = validity;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public void setKeyPass(String keyPass) {
        this.keyPass = keyPass.toCharArray();
    }
}