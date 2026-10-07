package com.pki.ca.controllers;

import com.pki.ca.entities.Signer;
import com.pki.ca.model.dto.RegisterWithQuestionsDTO;
import com.pki.ca.model.dto.RevokeCertificateRequestDTO;
import com.pki.ca.repositories.SignerRepo;
import com.pki.ca.services.SignerService;
import com.pki.ca.services.RevokedCertificateService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/ca/signer")
public class SignerController {

    private final SignerService signerService;
    private final RevokedCertificateService revokedCertificateService;

    public SignerController(SignerService signerService,
                            RevokedCertificateService revokedCertificateService) {
        this.signerService = signerService;
        this.revokedCertificateService = revokedCertificateService;
    }

    @GetMapping("/exists")
    public ResponseEntity<Boolean> exists(@RequestParam String email) {
        return ResponseEntity.ok(signerService.existsByEmail(email));
    }


    //ביטול תעודה עי בדיקה שהתעודה שייכת למשתמש
    @PostMapping("/certificate/revoke")
    public ResponseEntity<?> revokeCertificate(@RequestBody RevokeCertificateRequestDTO req) {
        try {
            revokedCertificateService.revokeCertificate(
                    req.getEmail(),
                    req.getSerialNumber(),
                    req.getReasonCode(),
                    req.getReasonText()
            );
            return ResponseEntity.ok("CERTIFICATE_REVOKED");
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("REVOCATION_ERROR");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestParam String firstName,
                                      @RequestParam String lastName,
                                      @RequestParam String email,
                                      @RequestParam String birthdate,
                                      @RequestParam("front") MultipartFile front,
                                      @RequestParam("back") MultipartFile back) {

        if(signerService.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("SIGNER_ALREDY_EXISTS");
        }

        try{
            Signer signer = new Signer();
            signer.setFirstName(firstName);
            signer.setLastName(lastName);
            signer.setEmail(email);
            signer.setBirthDate(LocalDate.parse(birthdate));
            signer.setIdCardFront(front.getBytes());
            signer.setIdCardBack(back.getBytes());

            return ResponseEntity.ok(signerService.createAccount(signer));
        } catch (IOException e){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("IMAGE_ERROR");
        }
    }

    //רישום משתמש עם שאלות האבטחה שבחר
    @PostMapping("/registerWithQuestions")
    public ResponseEntity<?> registerWithQuestions(@RequestBody RegisterWithQuestionsDTO req) {
        try {
            Signer signer = signerService.createAccountWithQuestions(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(signer);
        } catch (IllegalArgumentException e) {
            if(e.getMessage().contains("ALREADY_EXISTS")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("SIGNER_ALREADY_EXISTS");
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("REGISTRATION_ERROR: " + e.getMessage());
        }
    }

}
