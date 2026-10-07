package com.pki.ca.model.dto;

import java.util.*;

public class SecurityAnswersRequestDTO {

    private String email;
    private Map<String, String> answers;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Map<String, String> getAnswers() { return answers; }
    public void setAnswers(Map<String, String> answers) { this.answers = answers; }
}
