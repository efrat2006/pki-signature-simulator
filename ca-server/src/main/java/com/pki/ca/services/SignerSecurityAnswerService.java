package com.pki.ca.services;

import com.pki.ca.entities.SecurityQuestion;
import com.pki.ca.entities.Signer;
import com.pki.ca.entities.SignerSecurityAnswer;
import com.pki.ca.repositories.SecurityQuestionRepo;
import com.pki.ca.repositories.SignerRepo;
import com.pki.ca.repositories.SignerSecurityAnswerRepo;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class SignerSecurityAnswerService {

    private final SignerSecurityAnswerRepo signerSecurityAnswerRepo;
    private final SignerRepo signerRepo;
    private final SecurityQuestionRepo securityQuestionRepo;
    private final BCryptPasswordEncoder passwordEncoder;
    private static final String DUMMY_HASH = "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/zBkqquDzH8v.yHn4.J8.M1.5cW";

    public SignerSecurityAnswerService(SignerSecurityAnswerRepo signerSecurityAnswerRepo, SecurityQuestionRepo securityQuestionRepo, SignerRepo signerRepo) {
        this.signerSecurityAnswerRepo = signerSecurityAnswerRepo;
        this.signerRepo = signerRepo;
        this.securityQuestionRepo = securityQuestionRepo;
        this.passwordEncoder = new BCryptPasswordEncoder(12);
    }

    @Transactional
    public void saveAnswers(Long signerId, Map<Long, String> answers) {
        Signer signer = signerRepo.findById(signerId)
                .orElseThrow(() -> new IllegalArgumentException("Signer not found with id: " + signerId));

        for(Map.Entry<Long, String> entry : answers.entrySet()) {
            String answerText = entry.getValue();

            if (answerText == null || answerText.trim().isEmpty()) {
                System.out.println("Skipping null/empty answer for question: " + entry.getKey());
                continue;
            }

            char[] cleanAnswer = answerText.trim().toLowerCase().toCharArray();
            try{
                String hashed = passwordEncoder.encode(new String(cleanAnswer));
                SecurityQuestion question = securityQuestionRepo.findById(entry.getKey())
                        .orElseThrow(() -> new IllegalArgumentException("Question not found with id: " + entry.getKey()));
                signerSecurityAnswerRepo.save(new SignerSecurityAnswer(signer, question, hashed));
            } finally {
                Arrays.fill(cleanAnswer, '\0');
            }

        }
    }

    //מחזירה את השאלות האבטחה אותן בחר המשתמש
    @Transactional(readOnly = true)
    public List<String> getQuestionsForSignerEmail(String email) {
        return signerRepo.findByEmail(email)
                .map(signer -> signerSecurityAnswerRepo.findBySigner_Id(signer.getId()).stream()
                        .map(a -> a.getQuestion().getQuestionText())
                        .toList())
                .orElseGet(List::of);
    }

    public boolean verifyAnswers(Long signerId, Map<Long, String> answersToVerify) {
        List<SignerSecurityAnswer> savedAnswers = signerSecurityAnswerRepo.findBySigner_Id(signerId);
        if(savedAnswers.isEmpty()) {
            //אם משתשמ שלא קיים נבצע השוואת דמה כדי לא להסגיר להאקרים שהוא לא קיים
            passwordEncoder.matches("dummy_data", DUMMY_HASH);
            return false;
        }

        boolean allMatch = true;

        for(Map.Entry<Long, String> entry : answersToVerify.entrySet()) {
            String answerText = entry.getValue();

            if (answerText == null || answerText.trim().isEmpty()) {
                allMatch = false;
                continue;
            }

            char[] cleanAnswer = answerText.trim().toLowerCase().toCharArray();
            try{
                SignerSecurityAnswer saved = savedAnswers.stream()
                        .filter(a -> a.getQuestion().getId().equals(entry.getKey()))
                        .findFirst().orElse(null);

                String hasToVerify;
                if (saved != null) {
                    hasToVerify = saved.getAnswerHash();
                } else {
                    hasToVerify = DUMMY_HASH;
                }

                if(!passwordEncoder.matches(new String(cleanAnswer), hasToVerify)) {
                    allMatch = false;
                }
            } finally {
                Arrays.fill(cleanAnswer, '\0');
            }
        }
        return allMatch;
    }

    //שומרת את התשובות האבטחה של המשתמש
    public void saveAnswersNoTransaction(Signer signer, Map<Long, String> answers) {
        if (answers == null || answers.isEmpty()) {
            System.out.println("No answers to save");
            return;
        }

        for (Map.Entry<Long, String> entry : answers.entrySet()) {
            String answerText = entry.getValue();

            if (answerText == null || answerText.trim().isEmpty()) {
                System.out.println("Skipping null/empty answer for question: " + entry.getKey());
                continue;
            }

            char[] cleanAnswer = answerText.trim().toLowerCase().toCharArray();
            try {
                String hashed = passwordEncoder.encode(new String(cleanAnswer));
                SecurityQuestion question = securityQuestionRepo.findById(entry.getKey())
                        .orElseThrow(() -> new IllegalArgumentException("Question not found with id: " + entry.getKey()));
                signerSecurityAnswerRepo.save(new SignerSecurityAnswer(signer, question, hashed));
                System.out.println("Answer saved for question: " + entry.getKey());
            } catch (Exception e) {
                System.err.println("Error saving answer: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Failed to save answer for question " + entry.getKey(), e);
            } finally {
                Arrays.fill(cleanAnswer, '\0');
            }
        }
    }
}