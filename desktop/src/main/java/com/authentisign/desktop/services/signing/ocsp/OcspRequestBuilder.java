package com.authentisign.desktop.services.signing.ocsp;

import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.cert.ocsp.jcajce.JcaCertificateID;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class OcspRequestBuilder {

    private byte[] currentNonce;
    //בניית אובייקט בקשה לבדיקת OCSP
    public OCSPReq buildOCSPRequest(X509Certificate issuerCert, BigInteger serialNumber) throws Exception {
        DigestCalculator digestCalc = new JcaDigestCalculatorProviderBuilder()
                .build().get(CertificateID.HASH_SHA1);

        CertificateID certId = new JcaCertificateID(digestCalc, issuerCert, serialNumber);
        OCSPReqBuilder builder = new OCSPReqBuilder();
        builder.addRequest(certId);

        //הוספת מספר רנדומלי מפני תקיפת שחזור
        ExtensionsGenerator extGen = new ExtensionsGenerator();
        currentNonce = new byte[32];
        new SecureRandom().nextBytes(currentNonce);
        extGen.addExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce, false, new DEROctetString(currentNonce));
        builder.setRequestExtensions(extGen.generate());
        return builder.build();
    }
}