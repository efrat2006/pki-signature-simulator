package com.pki.ca.services;

import com.pki.ca.certs.ca.CaKeyService;
import com.pki.ca.entities.Certificate;
import com.pki.ca.entities.RevokedCertificate;
import com.pki.ca.repositories.CertificateRepo;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.*;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigInteger;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;

@Service
public class OcspService {

    private static final Logger logger = LoggerFactory.getLogger(OcspService.class);

    private final CaKeyService caKeyService;
    private final CertificateRepo certificateRepo;
    private final RevokedCertificateService revokedCertificateService;

    public OcspService(CaKeyService caKeyService,
                       CertificateRepo certificateRepo,
                       RevokedCertificateService revokedCertificateService) {
        this.caKeyService = caKeyService;
        this.certificateRepo = certificateRepo;
        this.revokedCertificateService = revokedCertificateService;
    }

    public byte[] processRequest(byte[] requestBytes) throws Exception {
        OCSPReq ocspReq = new OCSPReq(requestBytes);
        Req[] requestList = ocspReq.getRequestList();

        if (requestList == null || requestList.length == 0) {
            return buildErrorResponse(OCSPRespBuilder.MALFORMED_REQUEST);
        }

        // המרת המפתח הציבורי של ה-CA לפורמט הנדרש
        SubjectPublicKeyInfo pubKeyInfo = SubjectPublicKeyInfo.getInstance(
                caKeyService.getCaCertificate().getPublicKey().getEncoded()
        );

        BasicOCSPRespBuilder respBuilder = new BasicOCSPRespBuilder(
                pubKeyInfo,
                new JcaDigestCalculatorProviderBuilder().setProvider("BC").build().get(CertificateID.HASH_SHA1)
        );

        for (Req req : requestList) {
            CertificateID certId = req.getCertID();
            CertificateStatus status = resolveStatus(certId.getSerialNumber());
            respBuilder.addResponse(certId, status);
        }

        copyNonce(ocspReq, respBuilder);

        ContentSigner signer = new JcaContentSignerBuilder("Ed25519")
                .setProvider("BC")
                .build(caKeyService.getCaPrivateKey());

        X509CertificateHolder caHolder = new JcaX509CertificateHolder(caKeyService.getCaCertificate());

        BasicOCSPResp basicResp = respBuilder.build(signer, new X509CertificateHolder[]{caHolder}, new Date());

        return new OCSPRespBuilder().build(OCSPRespBuilder.SUCCESSFUL, basicResp).getEncoded();
    }

    private CertificateStatus resolveStatus(BigInteger serialNumber) {
        String serialStr = serialNumber.toString();
        Optional<Certificate> certOpt = certificateRepo.findBycertSerialNumber(serialStr);

        if (certOpt.isEmpty()) {
            return new UnknownStatus();
        }

        Certificate certificate = certOpt.get();
        Optional<RevokedCertificate> revoked = revokedCertificateService.isRevoked(certificate);

        if (revoked.isPresent()) {
            return new RevokedStatus(Date.from(revoked.get().getRevocationDate().atZone(ZoneId.systemDefault()).toInstant()), 0);
        }

        return CertificateStatus.GOOD;
    }

    private void copyNonce(OCSPReq ocspReq, BasicOCSPRespBuilder respBuilder) {
        Extension nonce = ocspReq.getExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce);
        if (nonce != null) {
            respBuilder.setResponseExtensions(new Extensions(nonce));
        }
    }

    private byte[] buildErrorResponse(int status) throws OCSPException, IOException {
        return new OCSPRespBuilder().build(status, null).getEncoded();
    }
}