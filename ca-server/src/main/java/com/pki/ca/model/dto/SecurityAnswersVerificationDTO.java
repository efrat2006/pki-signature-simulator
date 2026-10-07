package com.pki.ca.model.dto;

import java.util.Map;

public class SecurityAnswersVerificationDTO {
    private String email;
    private Map<String, String> answers;

    public SecurityAnswersVerificationDTO() {
    }

    public SecurityAnswersVerificationDTO(String email, Map<String, String> answers) {
        this.email = email;
        this.answers = answers;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Map<String, String> getAnswers() {
        return answers;
    }

    public void setAnswers(Map<String, String> answers) {
        this.answers = answers;
    }

    @Override
    public String toString() {
        return "SecurityAnswersVerificationDTO{" +
                "email='" + email + '\'' +
                ", answers=" + (answers != null ? answers.keySet() : "null") +
                '}';
    }
}
