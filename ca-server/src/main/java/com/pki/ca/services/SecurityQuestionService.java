package com.pki.ca.services;

import com.pki.ca.entities.*;
import com.pki.ca.repositories.SecurityQuestionRepo;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SecurityQuestionService {

    private final SecurityQuestionRepo securityQuestionRepo;

public SecurityQuestionService(SecurityQuestionRepo securityQuestionRepository) {
    this.securityQuestionRepo = securityQuestionRepository;
}

    //שליפת כל השאלות הפעילות
    public List<SecurityQuestion> getAllActiveQuestions() {
        return securityQuestionRepo.findByIsActiveTrue();
    }

    public Optional<SecurityQuestion> getQuestionById(Long questionId) {
        return securityQuestionRepo.findById(questionId);
    }
}
