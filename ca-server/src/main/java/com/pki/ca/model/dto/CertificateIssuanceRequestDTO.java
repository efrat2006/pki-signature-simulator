package com.pki.ca.model.dto;

import java.util.Map;

public class CertificateIssuanceRequestDTO {
    private String email;
    private String csrPem;
    private Map<String, String> answers;

    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }

    public String getCsrPem() {
        return csrPem;
    }
    public void setCsrPem(String csrPem) {
        this.csrPem = csrPem;
    }

    public Map<String, String> getAnswers() {
        return answers;
    }
    public void setAnswers(Map<String, String> answers) {
        this.answers = answers;
    }
}