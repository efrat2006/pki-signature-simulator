package com.pki.ca.controllers;

import com.pki.ca.entities.SecurityQuestion;
import com.pki.ca.entities.Signer;
import com.pki.ca.model.dto.SecurityAnswersVerificationDTO;
import com.pki.ca.model.dto.SecurityAnswersRequestDTO;
import com.pki.ca.repositories.SecurityQuestionRepo;
import com.pki.ca.repositories.SignerRepo;
import com.pki.ca.services.SignerSecurityAnswerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.*;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/ca/securityAnswers")
public class SignerSecurityAnswerController {

    private final SignerSecurityAnswerService answerService;
    private final SignerRepo signerRepo;
    private final SecurityQuestionRepo questionRepo;

    public  SignerSecurityAnswerController(SignerSecurityAnswerService answerService,  SignerRepo signerRepo, SecurityQuestionRepo questionRepo) {
        this.answerService = answerService;
        this.signerRepo = signerRepo;
        this.questionRepo = questionRepo;
    }

    //שמירת שאלות אבטחה
    @PostMapping("/saveAnswers")
    public ResponseEntity<Void> saveAnswers(@RequestBody SecurityAnswersRequestDTO req){
        Long signerId = resolveSignerId(req.getEmail());
        Map<Long, String> byQuestionId = toQuestionIdMap(req.getAnswers());
        answerService.saveAnswers(signerId, byQuestionId);
        return ResponseEntity.ok().build();
    }



    @PostMapping("/verify")
    public ResponseEntity<?> verifySecurityAnswers(@RequestBody SecurityAnswersVerificationDTO req) {
        try {
            String email = req.getEmail();
            Map<String, String> answersByText = req.getAnswers();

            System.out.println("Verifying for email: " + email);
            System.out.println("   Answers count: " + answersByText.size());

            Long signerId = signerRepo.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("Signer not found: " + email))
                    .getId();

            System.out.println("   Signer ID: " + signerId);

            //המרת השאלות בID
            Map<Long, String> byQuestionId = toQuestionIdMap(answersByText);

            boolean verified = answerService.verifyAnswers(signerId, byQuestionId);

            if (!verified) {
                System.out.println("Verification FAILED for email: " + email);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Security answers verification failed");
            }

            System.out.println("Verification PASSED for email: " + email);
            return ResponseEntity.ok("Security answers verified successfully");

        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(e.getMessage());
        } catch (Exception e) {
            System.err.println("Verification error: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Verification failed: " + e.getMessage());
        }
    }

    private Long resolveSignerId(String email) {
        Signer signer = signerRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Signer not found: " + email));
        return signer.getId();
    }

    private Map<Long, String> toQuestionIdMap(Map<String, String> answersByText) {
        Map<String, Long> idByText = questionRepo.findByIsActiveTrue().stream()
                .collect(Collectors.toMap(SecurityQuestion::getQuestionText, SecurityQuestion::getId));

        Map<Long, String> result = new HashMap<>();
        for (Map.Entry<String, String> e : answersByText.entrySet()) {
            Long qId = idByText.get(e.getKey());
            if (qId == null) {
                throw new IllegalArgumentException("שאלה לא מוכרת: " + e.getKey());
            }
            result.put(qId, e.getValue());
        }
        return result;
    }
}
