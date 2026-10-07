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

public enum OcspStatus {
    GOOD,
    REVOKED,
    UNKNOWN,
    RESPONDER_UNAVAILABLE;
}