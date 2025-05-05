package prizm.crypto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.Date;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateException;
import java.util.logging.Level;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import prizm.Prizm;
import prizm.util.Convert;
import prizm.util.Logger;


public class SSLCert {
    private int keysize = 1024;
    private String commonName = "BenedCore";
    private String organizationalUnit = "Users";
    private String organization = "Bened Community";
    private String city = "Bened";
    private String state = "Bened";
    private String country = "EN";
    private long validity = 4384; // 3 years
    private String alias = "bened";
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
        } else
            return false;
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

    private String writeInternal(String path) {
         String fingerprint ="error fingerprint";
        try {
            this.pathToSave = path;

            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null, null);

//        CertAndKeyGen keypair = new CertAndKeyGen("RSA", "SHA1WithRSA", null);
//
//        X500Name x500Name = new X500Name(commonName, organizationalUnit, organization, city, state, country);
//        keypair.generate(keysize);
//        PrivateKey privKey = keypair.getPrivateKey();
            X509Certificate[] chain = new X509Certificate[1];
            X509Certificate selfSignedX509Certificate = generateSelfSignedX509Certificate();

//chain[0] = keypair.getSelfCertificate(x500Name, new Date(), (long) validity * 24 * 60 * 60);
            chain[0] = selfSignedX509Certificate;
            MessageDigest sha1 = MessageDigest.getInstance("SHA1");
            
            sha1.update(chain[0].getEncoded());

            fingerprint = Convert.toHexString(sha1.digest()).toUpperCase().replaceAll("(.{2})", "$1 ");

            keyStore.setKeyEntry(alias, keyPair.getPrivate(), keyPass, chain);

            keyStore.store(new FileOutputStream(this.pathToSave), keyPass);
            
        } catch (KeyStoreException | NoSuchAlgorithmException | IOException | CertificateException ex) {
            java.util.logging.Logger.getLogger(SSLCert.class.getName()).log(Level.SEVERE, null, ex);
        }finally{
            return fingerprint;
        }
            
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
    
    ////////////new 
//       public static void main(String[] args) throws CertificateEncodingException, InvalidKeyException, IllegalStateException, NoSuchProviderException, NoSuchAlgorithmException, SignatureException {
//        X509Certificate selfSignedX509Certificate = new BouncyCastle().generateSelfSignedX509Certificate();
//        System.out.println(selfSignedX509Certificate);
//    }

    private KeyPair keyPair;
    public X509Certificate generateSelfSignedX509Certificate(){
        final String host = Prizm.getStringProperty("bened.apiServerHost");
        String localhost = "0.0.0.0".equals(host) || "127.0.0.1".equals(host) ? "localhost" : host;
        
        
        
        Security.addProvider(new BouncyCastleProvider());
        X509Certificate cert =null;
        try{
        // generate a key pair
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC");
        keyPairGenerator.initialize(keysize, new SecureRandom());
        keyPair = keyPairGenerator.generateKeyPair();

        //        X500Name x500Name = new X500Name(commonName, organizationalUnit, organization, city, state, country);
       
        String fqdn = "CN="+localhost+", OU="+organizationalUnit+", O="+organization+", L="+city+", ST="+state+", C="+country;
                // build a certificate generator
        X509V3CertificateGenerator certGen = new X509V3CertificateGenerator();
        X500Principal dnName = new X500Principal(fqdn) ; //("cn="+host);

        // add some options
        certGen.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
        certGen.setSubjectDN(new X509Name(fqdn));
        certGen.setIssuerDN(dnName); // use the same

        // yesterday
        certGen.setNotBefore(new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000));
        // in 2 years
        certGen.setNotAfter(new Date(System.currentTimeMillis() + ((long)5 * ((long)1000*(long)60*(long)525600)) ));  
        certGen.setPublicKey(keyPair.getPublic());
        certGen.setSignatureAlgorithm("SHA256WithRSAEncryption");
        certGen.addExtension(X509Extensions.ExtendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));

        // finally, sign the certificate with the private key of the same KeyPair
        cert = certGen.generate(keyPair.getPrivate(), "BC");
        }catch(Exception e){
            Logger.logErrorMessage("\n----error ssl e="+e);
        }finally{
             return cert;
        }
       
    }

   
}