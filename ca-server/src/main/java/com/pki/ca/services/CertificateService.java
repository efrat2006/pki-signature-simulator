package com.pki.ca.services;
import com.pki.ca.entities.*;
import com.pki.ca.repositories.*;
import org.slf4j.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CertificateService {

    private static final Logger logger =
            LoggerFactory.getLogger(CertificateService.class);

    private final CertificateRepo certificateRepo;

    public CertificateService(CertificateRepo certificateRepo) {
        this.certificateRepo = certificateRepo;
    }

   //בידקת תקינות נתונים
    private void validateInput(Certificate certificate) {

        Long id = certificate.getId();
        validateId(id);
        if (certificate == null) {
            logger.warn("Invalid Certificate request: {}", certificate);
            throw new IllegalArgumentException("INVALID_INPUT");
        }
    }

   //בדיקה לפני שמירה
    public void validateForSave(Certificate certificate) {
        if(certificate == null){
            logger.warn("Attempt to save a null certificate request");
            throw new IllegalArgumentException("INVALID_INPUT");
        }
    }

    //בדיקת ID
    private void validateId(Long id) {
        if (id == null || id <= 0) {
            logger.warn("Invalid ID: {}", id);
            throw new IllegalArgumentException("INVALID_INPUT");
        }
    }

    //שמירת התעודה
    public Certificate save(Certificate certificate) {
        validateForSave(certificate);
        return certificateRepo.save(certificate);
    }



   //שליפת כל התעודות
    public List<Certificate> getAll() {
        return certificateRepo.findAll();
    }

    public List<Certificate> getByStatus(StatusType status) {
        if (status == null) {
            logger.warn("Status cannot be empty");
            throw new IllegalArgumentException("INVALID_REQUEST");
        }
        //שליחה לDB
        List<Certificate> result = certificateRepo.findByStatus(status);
        //בדיקה האם נמצאו תוצאות
        if (result.isEmpty()){
            logger.warn("No certificates with this status were found.");
            throw new RuntimeException("NOT_FOUND");
        }
        return result;
    }

    //שליפת כל התעודות החתומות של חותם
    public List<Certificate> getSignerCertsById(Long signerId) {
       validateId(signerId);
        return certificateRepo.findBySignerId(signerId);
    }
}