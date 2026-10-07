package com.pki.ca.certs.ca;

import jakarta.annotation.PostConstruct;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.*;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

@Service
public class CaKeyService {
    private static final Logger logger = LoggerFactory.getLogger(CaKeyService.class);

    private static final String KEY_ALGORITHM = "Ed25519";
    private static final String SIG_ALGORITHM = "Ed25519";
    private static final String CA_ALIAS = "ca";
    private static final String CA_DN = "CN=DigSign Root CA, OU=Security Division, O=DigSign, C=IL";

    //שליפה מתוך ההגדרות של המערכת
    @Value("${ca.keystore.dir:certs/ca}")
    private String keystoreDir;

    @Value("${ca.keystore.filename:root-ca.p12}")
    private String keystoreFileName;
    @Value("${ca.keystore.password:12345678}")
    private String keystorePassword;

    private PrivateKey caPrivateKey;
    private X509Certificate caCertificate;

    @PostConstruct
    public void init() {

        if(Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try{
            File directory = new File(keystoreDir);
            if (!directory.exists()){
                directory.mkdirs();
            }
            char[] password = keystorePassword.toCharArray();
            File file = new File(directory, keystoreFileName);

            if(file.exists()){
                loadIdentity(file, password);
                logger.info("Keystore file has been created");
            } else{
                creatAndStoreIdentity(file, password);
                logger.info("CA identity created and stored at {}", keystoreFileName);
            }
        }  catch(Exception e){
            throw new IllegalStateException("Failed to initialize CA identity: " + e.getMessage(), e);
        }
    }

    private void loadIdentity(File file, char[] password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try(FileInputStream fis = new FileInputStream(file)){
            ks.load( fis, password);
        }
        this.caPrivateKey = (PrivateKey) ks.getKey(CA_ALIAS, password);
        this.caCertificate = (X509Certificate) ks.getCertificate(CA_ALIAS);
        if(caPrivateKey == null || caCertificate == null){
            throw new IllegalStateException("Keystore not found");
        }
    }

    private void creatAndStoreIdentity(File file, char[] password) throws Exception {
        KeyPair caKeyPair = generateKeyPair();
        X509Certificate cert = generateSelfSignedCaCertificate(caKeyPair);

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null ,null);
        //מחבר בין התעודה למפתח הפרטי ושומר בתוך האובייקט keyStore בזיכרון
        ks.setKeyEntry(CA_ALIAS, caKeyPair.getPrivate(), password, new Certificate[]{cert});
        //כתיבת המפתחות המוצפנים על דיסק פיזי
        try(FileOutputStream fos = new FileOutputStream(file)){
            ks.store(fos, password);
        }

        this.caPrivateKey = caKeyPair.getPrivate();
        this.caCertificate = cert;
    }

    private KeyPair generateKeyPair() throws Exception{
        KeyPairGenerator gen = KeyPairGenerator.getInstance(KEY_ALGORITHM);
        return gen.generateKeyPair();
    }

    private X509Certificate generateSelfSignedCaCertificate(KeyPair caKeyPair) throws Exception{
        X500Name caName = new X500Name(CA_DN);
        PublicKey publicKey = caKeyPair.getPublic();
        PrivateKey privateKey = caKeyPair.getPrivate();

        long now = System.currentTimeMillis();
        Date startDate = new Date(now);
        Date expiryDate = new Date(now + (3650L * 24 * 60 * 60 * 1000));   //10 שנים
        BigInteger serial = new BigInteger(159, new SecureRandom());

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                caName,
                serial,
                startDate,
                expiryDate,
                caName,
                publicKey
        );

        //אומר למערכת שהתעודה הזאת היא רשות אישורים ומותר לה לחתום על מערכות אחרות
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.digitalSignature));

        AccessDescription ocsp = new AccessDescription(AccessDescription.id_ad_ocsp, new GeneralName(GeneralName.uniformResourceIdentifier, "https://localhost:8080/api/ca/ocsp"));
        builder.addExtension(Extension.authorityInfoAccess, false, new AuthorityInformationAccess(ocsp));
        ContentSigner signer = new JcaContentSignerBuilder(SIG_ALGORITHM).setProvider("BC").build(privateKey);
        //המרה לאובייקט X509Certificate
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer));
    }

    public PrivateKey getCaPrivateKey() {
        return this.caPrivateKey;
    }

    public X509Certificate getCaCertificate() {
        return this.caCertificate;
    }

}
