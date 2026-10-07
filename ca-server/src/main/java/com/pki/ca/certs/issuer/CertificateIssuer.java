package com.pki.ca.certs.issuer;

import com.pki.ca.strategies.SigningStrategy;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.operator.ContentSigner;
import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import org.bouncycastle.asn1.x509.BasicConstraints;

import java.util.Date;

//בניית התעודה לחותם
public class CertificateIssuer {

    public static X509Certificate issueCertificate(
            PublicKey userPublicKey,
            PrivateKey caPrivateKey,
            X500Name caIssuerName,
            X500Name userSubjectName,
            SigningStrategy algorithm,
            X509Certificate caCert) throws Exception{

        long now = System.currentTimeMillis();
        Date startDate = new Date(now);
        Date expiryDate = new Date(now + (365L * 24 * 60 * 60 * 1000));
        BigInteger certSerialNumber = new BigInteger(159, new SecureRandom());

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                caIssuerName,
                certSerialNumber,
                startDate,
                expiryDate,
                userSubjectName,
                userPublicKey
        );

        //השורה הזאת קובעת אם התעודה שייכת ל-CA או למשתמש
        certBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        //מאשר להשתמש בתעודה לצורך חתימה דיגיטלית על קבצים
        certBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));

        ContentSigner contentSigner = new SigningStrategyAdapter(algorithm, caPrivateKey, caCert);

        return new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certBuilder.build(contentSigner));

    }

}