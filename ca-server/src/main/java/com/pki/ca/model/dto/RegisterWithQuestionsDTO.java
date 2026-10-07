package com.pki.ca.model.dto;

import java.util.Map;

public class RegisterWithQuestionsDTO {
    private String firstName;
    private String lastName;
    private String email;
    private String birthDate;
    private String idCardFrontBase64;
    private String idCardBackBase64;
    private Map<String, String> answers;

    public String getFirstName() { return firstName; }
    public void setFirstName(String v) { this.firstName = v; }
    public String getLastName() { return lastName; }
    public void setLastName(String v) { this.lastName = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getBirthDate() { return birthDate; }
    public void setBirthDate(String v) { this.birthDate = v; }
    public String getIdCardFrontBase64() { return idCardFrontBase64; }
    public void setIdCardFrontBase64(String v) { this.idCardFrontBase64 = v; }
    public String getIdCardBackBase64() { return idCardBackBase64; }
    public void setIdCardBackBase64(String v) { this.idCardBackBase64 = v; }
    public Map<String, String> getAnswers() { return answers; }
    public void setAnswers(Map<String, String> v) { this.answers = v; }
}