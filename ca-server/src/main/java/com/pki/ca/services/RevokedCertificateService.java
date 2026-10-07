package com.pki.ca.services;

import com.pki.ca.entities.*;
import com.pki.ca.repositories.CertificateRepo;
import com.pki.ca.repositories.RevokedCertRepo;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class RevokedCertificateService {

    private static final Logger logger = LoggerFactory.getLogger(RevokedCertificateService.class);

    private final CertificateRepo certificateRepo;
    private final RevokedCertRepo revokedCertRepo;

    public RevokedCertificateService(RevokedCertRepo revokedCertRepo, CertificateRepo certificateRepo) {
        this.certificateRepo = certificateRepo;
        this.revokedCertRepo = revokedCertRepo;
    }


    //בדוק האם התעודה בטלה
    public Optional<RevokedCertificate> isRevoked(Certificate certificate) {
        if(certificate == null){
            logger.warn("Attempt to check revocation status of null certificate");
            return Optional.empty();
        }

        Optional<RevokedCertificate> result = revokedCertRepo.findByCertSerialNumber(certificate);
        return result;
    }


    //ביטול התעודה
    @Transactional
    public RevokedCertificate revokeCertificate(String email, String serialNumber, String reasonCode, String reasonText) {
        if (email == null || email.isBlank() || serialNumber == null || serialNumber.isBlank()) {
            logger.warn("Revoke request missing email or serial number");
            throw new IllegalArgumentException("INVALID_INPUT");
        }

        Certificate certificate = certificateRepo.findBycertSerialNumber(serialNumber)
                .orElseThrow(() -> {
                    logger.warn("Revoke request for unknown certificate serial {}", serialNumber);
                    return new NoSuchElementException("CERTIFICATE_NOT_FOUND");
                });

        Signer signer = certificate.getSigner();
        if (signer == null || signer.getEmail() == null || !signer.getEmail().equalsIgnoreCase(email)) {
            logger.warn("Revoke request for certificate {} by non-owner email {}", serialNumber, email);
            throw new IllegalArgumentException("CERTIFICATE_NOT_OWNED_BY_SIGNER");
        }

        if(revokedCertRepo.findByCertSerialNumber(certificate).isPresent()){
            logger.info("Certificate {} already revoked", serialNumber);
            throw new IllegalArgumentException("CERTIFICATE_ALREADY_REVOKED");
        }

        certificate.setStatus(StatusType.REVOKED);
        certificateRepo.save(certificate);

        String reason = (reasonCode == null || reasonCode.isBlank()) ? "UNSPECIFIED" : reasonCode;
        if(reasonText != null && !reasonText.isBlank()){
            reason = reason +": " + reasonText.trim();
        }

        RevokedCertificate revokedCertificate = new RevokedCertificate();
        revokedCertificate.setCertSerialNumber(certificate);
        revokedCertificate.setRevocationReason(reason);

        RevokedCertificate saved = revokedCertRepo.save(revokedCertificate);
        logger.info("Certificate {} revoked successfully for signer {}", serialNumber, email);
        return saved;
    }

}