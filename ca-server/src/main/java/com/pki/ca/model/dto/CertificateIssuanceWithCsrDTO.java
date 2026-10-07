package com.pki.ca.model.dto;

public class CertificateIssuanceWithCsrDTO {
    private String email;
    private String csrPem;

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
}