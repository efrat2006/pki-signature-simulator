package com.pki.ca.controllers;


import com.pki.ca.entities.SecurityQuestion;
import com.pki.ca.entities.Signer;
import com.pki.ca.model.dto.RegisterWithQuestionsDTO;
import com.pki.ca.model.dto.SecurityAnswersRequestDTO;
import com.pki.ca.repositories.SecurityQuestionRepo;
import com.pki.ca.repositories.SignerRepo;
import com.pki.ca.services.SecurityQuestionService;
import com.pki.ca.services.SignerSecurityAnswerService;
import com.pki.ca.services.SignerService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ca/securityQuestions")
public class SecurityQuestionController {

    private final SecurityQuestionService questionService;
    private final SignerSecurityAnswerService answerService;


    public SecurityQuestionController(SecurityQuestionService questionService,
                                      SignerSecurityAnswerService answerService) {
        this.questionService = questionService;
        this.answerService = answerService;
    }

    //שליפת כל השאלות הפעילות
    @GetMapping("/getAllSecurityQuestions")
    public ResponseEntity<List<String>> getAllSecurityQuestions() {
        return ResponseEntity.ok(questionService.getAllActiveQuestions().stream()
                .map(SecurityQuestion::getQuestionText)
                .collect(Collectors.toList()));
    }

    //שליפת שאלות האבטחה שהמשתמש הספציפי בחר
    @GetMapping("/user")
    public ResponseEntity<List<String>> getUserSecurityQuestions(@RequestParam String email) {
        return ResponseEntity.ok(answerService.getQuestionsForSignerEmail(email));
    }


}
