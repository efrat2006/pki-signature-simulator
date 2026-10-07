package com.pki.ca.model.dto;

public class RevokeCertificateRequestDTO {
    private String email;
    private String serialNumber;
    private String reasonCode;
    private String reasonText;

    public String getEmail() {
        return email;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getReasonText() {
        return reasonText;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public void setReasonText(String reasonText) {
        this.reasonText = reasonText;
    }
}
