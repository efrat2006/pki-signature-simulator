package com.authentisign.desktop.services.signing.pdf;

import com.authentisign.desktop.security.strategies.SigningStrategy;
import com.authentisign.desktop.services.signing.ocsp.OcspService;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.cms.*;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import java.io.IOException;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;


public class PdfSignatureHandler implements SignatureInterface {


    //OID - מזהה מספרי בינלאומי לכל אלגוריתם קריפטוגרפי בעולם
    private static final ASN1ObjectIdentifier ED25519_OID = new ASN1ObjectIdentifier("1.3.101.112");
    private static final ASN1ObjectIdentifier SHA512_OID = new ASN1ObjectIdentifier("2.16.840.1.101.3.4.2.3");

    private final SigningStrategy strategy;
    private final PrivateKey privateKey;
    private final Certificate[] chain;
    private final OcspService ocspService;

    public PdfSignatureHandler(SigningStrategy strategy, PrivateKey privateKey, Certificate[] chain, OcspService ocspService) {
        this.strategy = strategy;
        this.privateKey = privateKey;
        this.chain = chain;
        this.ocspService = ocspService;
    }

    //ממיר את שרשרת התעודות
    private X509Certificate[] toX509Array(Certificate[] chain) {
        X509Certificate[] result = new X509Certificate[chain.length];
        for (int i = 0; i < chain.length; i++) {
            result[i] = (X509Certificate) chain[i];
        }
        return result;
    }

    //מקבלת את תוכן הקובץ
    @Override
    public byte[] sign(InputStream content) throws IOException {
        try {
            X509Certificate cert = (X509Certificate) chain[0];
            cert.checkValidity();

            ocspService.verifyForSigning(toX509Array(chain));

            byte[] data = content.readAllBytes();
            byte[] signature = strategy.sign(data, privateKey, chain);
            return buildCMS(data, signature, cert, chain);

        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    //
    private byte[] buildCMS(byte[] data, byte[] signature, X509Certificate signerCert, Certificate[] chain) throws Exception {

        //ASN1EncodableVector - מבנה שאפשר לאסוף איתו כמה ערכים ואז לעטוף אותם במבני נתונים מסוג ASN.1
        ASN1EncodableVector digestAlgs = new ASN1EncodableVector();
        digestAlgs.add(new AlgorithmIdentifier(SHA512_OID));

        //הגדרת התוכן שנחתם
        ContentInfo encapContentInfo = new ContentInfo(
                PKCSObjectIdentifiers.data,
                null  // החתימה לא כלולה בתוכן
        );

        //הגדרת תעודות
        ASN1EncodableVector certVector = new ASN1EncodableVector();
        for (Certificate c : chain) {
            if (c instanceof X509Certificate) {
                JcaX509CertificateHolder ch = new JcaX509CertificateHolder((X509Certificate) c);
                certVector.add(ch.toASN1Structure());
            }
        }
        DERSet certs = new DERSet(certVector);

        //פרטי החותם
        SignerInfo signerInfo = buildSignerInfo(signature, signerCert);

        //יכולים להיות כמה חותמים
        ASN1EncodableVector signerInfos = new ASN1EncodableVector();
        signerInfos.add(signerInfo);

        SignedData signedData = new SignedData(
                new DERSet(digestAlgs),
                encapContentInfo,
                certs,
                null,  //לא משתמשים בCLR
                new DERSet(signerInfos)
        );

        ContentInfo contentInfo = new ContentInfo(
                PKCSObjectIdentifiers.signedData,
                signedData
        );

        return contentInfo.getEncoded(ASN1Encoding.DER);
    }

    //הפונקציה מצמידה את החתימה לזהות החותם
    private SignerInfo buildSignerInfo(byte[] signature, X509Certificate cert) throws Exception {
        // מזהה החותם - לפי issuer + serial number
        JcaX509CertificateHolder holder = new JcaX509CertificateHolder(cert);
        IssuerAndSerialNumber issuerSerial = new IssuerAndSerialNumber(
                holder.getIssuer(),
                holder.getSerialNumber()
        );
        SignerIdentifier signerId = new SignerIdentifier(issuerSerial);

        //מבנה האומר שהאלגוריתם HASH הוא SHA512_OID
        AlgorithmIdentifier digestAlg = new AlgorithmIdentifier(SHA512_OID);

        //מבנה האומר שהאלגוריתם החתימה הוא Ed25519
        AlgorithmIdentifier signatureAlg = new AlgorithmIdentifier(ED25519_OID);

        return new SignerInfo(
                signerId,
                digestAlg,
                (ASN1Set) null,      //תוספות מיוחדות
                signatureAlg,
                new DEROctetString(signature),
                (ASN1Set) null
        );
    }
}