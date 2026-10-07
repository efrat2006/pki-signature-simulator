package com.pki.ca.controllers;

import com.pki.ca.certs.issuer.CertificateIssuer;
import com.pki.ca.certs.validation.CsrValidator;
import com.pki.ca.strategies.Ed25519;
import com.pki.ca.entities.SecurityQuestion;
import com.pki.ca.entities.Signer;
import com.pki.ca.model.dto.CertificateIssuanceRequestDTO;
import com.pki.ca.model.dto.CertificateIssuanceWithCsrDTO;
import com.pki.ca.repositories.SecurityQuestionRepo;
import com.pki.ca.repositories.SignerRepo;
import com.pki.ca.services.SignerSecurityAnswerService;
import com.pki.ca.services.CertificateService;
import com.pki.ca.certs.ca.CaKeyService;
import com.pki.ca.entities.Certificate;
import com.pki.ca.entities.CertificateRequest;
import com.pki.ca.entities.StatusType;
import com.pki.ca.repositories.CertRequestRepo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("api/ca/issuance")
public class CertificateIssuanceController {

    private static final Logger log = LoggerFactory.getLogger(CertificateIssuanceController.class);

    @Autowired
    private CsrValidator csrValidator;

    @Autowired
    private SignerSecurityAnswerService answerService;

    @Autowired
    private SignerRepo signerRepo;

    @Autowired
    private SecurityQuestionRepo questionRepo;

    @Autowired
    private CaKeyService caKeyService;

    @Autowired
    private CertificateService certificateService;

    @Autowired
    private CertRequestRepo certRequestRepo;

    @PersistenceContext
    private EntityManager entityManager;


    @PostMapping("/issueCertificateWithCsr")
    @Transactional
    public ResponseEntity<?> issueCertificateWithCsr(
            @RequestBody CertificateIssuanceWithCsrDTO req,
            @RequestAttribute("authEmail") String authEmail) {
        try {
            Signer signer = csrValidator.findAndVerifySigner(authEmail);

            PKCS10CertificationRequest csr = csrValidator.parseCsr(req.getCsrPem());
            csrValidator.validate(csr);

            PublicKey userPublicKey = csrValidator.extractPublicKeyFromCsr(csr);

            X509Certificate certificate = issueCertificate(userPublicKey, signer.getFirstName(), signer.getLastName(), authEmail);
            persistCertificate(certificate, signer, userPublicKey, csr);
            return ResponseEntity.ok(certificate.getEncoded());

        } catch (Exception e) {
            log.error("issueCertificateWithCsr failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error: " + e.getMessage());
        }
    }

    //ארגון הנתוינם להנפקת התעודה
    private X509Certificate issueCertificate(PublicKey userPublicKey, String firstName, String lastName, String email) throws Exception {
        PrivateKey caPrivateKey = caKeyService.getCaPrivateKey();
        X509Certificate caCert = caKeyService.getCaCertificate();

        X500Name caIssuerName = new X500Name(caCert.getSubjectX500Principal().getName());
        //מוסכמה מקובלת בשביל בניית האובייקט ונתינת שמות
        X500NameBuilder builder = new X500NameBuilder(BCStyle.INSTANCE);
        builder.addRDN(BCStyle.CN, firstName + " " + lastName);
        builder.addRDN(BCStyle.O, "AuthentiSign");
        builder.addRDN(BCStyle.C, "IL");
        builder.addRDN(BCStyle.EmailAddress, email);

        return CertificateIssuer.issueCertificate(
                userPublicKey,
                caPrivateKey,
                caIssuerName,
                builder.build(),
                new Ed25519(),
                caCert
        );
    }

    //שמירת התעודה במסד + בקשת CSR
    private void persistCertificate(X509Certificate cert, Signer signer, PublicKey userPublicKey, PKCS10CertificationRequest csr) throws Exception {
        String publicKeyB64 = Base64.getEncoder().encodeToString(userPublicKey.getEncoded());

        CertificateRequest request = new CertificateRequest();
        request.setSigner(signer);
        request.setPublicKey(publicKeyB64);
        request.setSignature(Base64.getEncoder().encodeToString(csr.getSignature()));
        request.setRequestStatus(StatusType.APPROVED);
        request = certRequestRepo.save(request);

        Certificate entity = new Certificate();
        entity.setCertSerialNumber(cert.getSerialNumber().toString());
        entity.setSigner(signer);
        entity.setSignAlgorithm("Ed25519");
        entity.setCurveName("Ed25519");
        //קידוד תאריך הסיום ע"פ זמן בינלאומי
        entity.setExpiryDate(cert.getNotAfter().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        entity.setPublicKey(publicKeyB64);
        entity.setCertVersion(cert.getVersion());
        entity.setStatus(StatusType.ACTIVE);
        entity.setOrganization(signer.getOrganization());
        entity.setCertRequest(request);

        certificateService.save(entity);
        entityManager.flush();
    }

    //מחליפה את השאלה עצמה בID שלה
    private Map<Long, String> toQuestionIdMap(Map<String, String> answersByText) {
        Map<String, Long> idByText = questionRepo.findByIsActiveTrue().stream()
                .collect(Collectors.toMap(SecurityQuestion::getQuestionText, SecurityQuestion::getId));
        return answersByText.entrySet().stream()
                .collect(Collectors.toMap(e -> idByText.get(e.getKey()), Map.Entry::getValue));
    }
}