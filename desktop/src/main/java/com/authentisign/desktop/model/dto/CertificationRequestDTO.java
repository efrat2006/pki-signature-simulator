package com.authentisign.desktop.model.dto;

public class CertificationRequestDTO {
    private String csrPem;
    private boolean verified;
    private String algorithm;
    private String commonName;

    public CertificationRequestDTO() {}

    public CertificationRequestDTO(String csrPem, boolean verified, String algorithm, String commonName) {
        this.csrPem = csrPem;
        this.verified = verified;
        this.algorithm = algorithm;
        this.commonName = commonName;
    }

    public String getCsrPem() {
        return csrPem;
    }

    public boolean isVerified() {
        return verified;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getCommonName() {
        return commonName;
    }

    public void setCsrPem(String csrPem) {
        this.csrPem = csrPem;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public void setCommonName(String commonName) {
        this.commonName = commonName;
    }
}
