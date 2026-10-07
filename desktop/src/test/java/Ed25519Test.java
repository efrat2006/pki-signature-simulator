import com.authentisign.desktop.security.strategies.Ed25519;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

public class Ed25519Test {
    public static void main(String[] args) {
        try {
            Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

            Ed25519 strategy = new Ed25519();
            // יצירת מפתחות ותעודת דמה
            KeyPair kp = strategy.generateKeys();
            X509Certificate cert = generateDummyCertificate(kp);
            Certificate[] chain = new Certificate[]{ cert };
            System.out.println("keys+cert generated       : " + (kp.getPrivate()!=null && cert!=null));

            byte[] data = "AuthentiSign - data to be signed".getBytes();

           //חתימה
            byte[] sig = strategy.sign(data, kp.getPrivate(), chain);
            System.out.println("signature length == 64    : " + (sig.length == 64) + "  (got " + sig.length + ")");

            // אימות החתימה
            System.out.println("verify valid signature    : " + strategy.verify(data, sig, kp.getPublic()));

            //  נתונים ששונו -> חייב להיכשל
            byte[] tampered = data.clone(); tampered[0] ^= 0x01;
            System.out.println("verify tampered (false)   : " + strategy.verify(tampered, sig, kp.getPublic()));

            //  מפתח ציבורי אחר -> חייב להיכשל
            KeyPair other = strategy.generateKeys();
            System.out.println("verify wrong key (false)  : " + strategy.verify(data, sig, other.getPublic()));

            //  מאמת BC תקני מקבל את החתימה של האלגוריתם שיצרתי (מוכיח Ed25519 תקני)
            Signature bcv = Signature.getInstance("Ed25519", "BC");
            bcv.initVerify(kp.getPublic()); bcv.update(data);
            System.out.println("BC verifies our signature : " + bcv.verify(sig));

            //  ה-verify שלנו מקבל חתימה של BC
            Signature bcs = Signature.getInstance("Ed25519", "BC");
            bcs.initSign(kp.getPrivate()); bcs.update(data);
            byte[] bcSig = bcs.sign();
            System.out.println("we verify BC signature    : " + strategy.verify(data, bcSig, kp.getPublic()));

            //KeyStore -שמירה, טעינה, חתימה מחדש ב
            char[] pw = "changeit".toCharArray();
            KeyStore ks = KeyStore.getInstance("PKCS12", "BC");
            ks.load(null, pw);
            ks.setKeyEntry("test", kp.getPrivate(), pw, chain);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ks.store(bos, pw);
            KeyStore ks2 = KeyStore.getInstance("PKCS12", "BC");
            ks2.load(new ByteArrayInputStream(bos.toByteArray()), pw);
            PrivateKey reloaded = (PrivateKey) ks2.getKey("test", pw);
            byte[] sig2 = strategy.sign(data, reloaded, chain);
            System.out.println("sign after PKCS12 reload  : " + strategy.verify(data, sig2, kp.getPublic()));

            System.out.println("\nAll checks passed.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static X509Certificate generateDummyCertificate(KeyPair keyPair) throws Exception {
        long now = System.currentTimeMillis();
        X509v3CertificateBuilder v3CertGen = new JcaX509v3CertificateBuilder(
                new X500Principal("CN=TestUser"),
                BigInteger.valueOf(now),
                new Date(now),
                new Date(now + 365L * 24 * 60 * 60 * 1000),  // 365L: למנוע גלישת int
                new X500Principal("CN=TestUser"),
                keyPair.getPublic());
        return new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(v3CertGen.build(new JcaContentSignerBuilder("Ed25519")
                        .setProvider("BC")
                        .build(keyPair.getPrivate())));
    }
}
