package com.pki.ca.services;

import com.pki.ca.entities.*;
import com.pki.ca.model.dto.RegisterWithQuestionsDTO;
import com.pki.ca.repositories.SecurityQuestionRepo;
import com.pki.ca.repositories.SignerRepo;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class SignerService {

    private static final Logger logger =
            LoggerFactory.getLogger(SignerService.class);

    private final SignerRepo signerRepo;
    private final SignerSecurityAnswerService answerService;
    private final SecurityQuestionRepo questionRepo;

    public SignerService(SignerRepo signerRepo,  SignerSecurityAnswerService answerService, SecurityQuestionRepo questionRepo) {
        this.signerRepo = signerRepo;
        this.answerService = answerService;
        this.questionRepo = questionRepo;
    }

   //בדיקת תקינות ID
    public void validateId(Long id) {
        if (id == null || id <= 0) {
            logger.warn("Invalid ID: {}", id);
            throw new IllegalArgumentException("INVALID_INPUT");
        }
    }

    //בדיקת תקינות
    public void validateInput(Signer signer){

        if (signer == null){
            logger.warn("Invalid input");
            throw new IllegalArgumentException("SIGNER IS NULL");
        }
    }

   //בדיקת תקינות חותם
    public void validateSigner(Signer signer) {
        if(signer == null){
            logger.warn("Attempt to save a null signer");
            throw new IllegalArgumentException("INVALID_INPUT");
        }
    }

   //יצירת חשבון
    @Transactional
    public Signer createAccount(Signer signer)
    {
        validateSigner(signer);
        logger.info("Creating account for signer {}", signer.getFirstName() + signer.getLastName());
        return signerRepo.save(signer);
    }

    //עדכון חותם
    public Signer update(Signer signer){
        validateInput(signer);
        return signerRepo.save(signer);
    }

   //מחיקה
    public void delete(Signer signer) {
        validateInput(signer);
        signerRepo.delete(signer);
    }

   //קבלת ID של החותם
    public Signer getById(Long id) {
        validateId(id);
        return signerRepo.findById(id)
                .orElseThrow(() ->{
                    logger.error("SYSTEM ERROR: Signer with ID {} not found", id);
                    return new RuntimeException("Signer not found");
                });
    }

   //שליפת כל החותמים
    public List<Signer> getAll() {
        return signerRepo.findAll();
    }

    //קבלת חותם לפי המייל
    public Signer getByEmail(String email) {
        //הסדר משנה כדי שלא יקרוס
        if (email == null || email.isBlank()) {
            logger.warn("Email is null or empty");
            throw new IllegalArgumentException("INVALID_INPUT");
        }
        return signerRepo.findByEmail(email).orElse(null);
    }

    public boolean existsByEmail(String email) {
        return signerRepo.findByEmail(email).isPresent();

    }

    //יצירת חשבון עם שאלות אבטחה
    @Transactional
    public Signer createAccountWithQuestions(RegisterWithQuestionsDTO req) {
        if(existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("SIGNER_ALREADY_EXISTS");
        }

        Signer signer = new Signer();
        signer.setFirstName(req.getFirstName());
        signer.setLastName(req.getLastName());
        signer.setEmail(req.getEmail());
        signer.setBirthDate(LocalDate.parse(req.getBirthDate()));
        signer.setIdCardFront(Base64.getDecoder().decode(req.getIdCardFrontBase64()));
        signer.setIdCardBack(Base64.getDecoder().decode(req.getIdCardBackBase64()));

        Map<Long, String> answersMap = req.getAnswers().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> questionRepo.findByQuestionText(entry.getKey())
                                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + entry.getKey()))
                                .getId(),
                        Map.Entry::getValue
                ));

        Signer saved = signerRepo.save(signer);
        answerService.saveAnswersNoTransaction(saved, answersMap);
        return saved;
    }

}
