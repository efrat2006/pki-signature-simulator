package com.authentisign.desktop.model.dto;

import java.time.LocalDateTime;

public class IdentityVerificationDTO {

    private final String firstName;
    private final String lastName;
    private final LocalDateTime dateOfBirth;
//    private final byte[] idCardFront;
//    private final byte[] idCardBack;


    public IdentityVerificationDTO(String firstname, String lastname, LocalDateTime dateOfBirth

//                                   byte[] idCardFront, byte[] idCardBack
    ) {
        this.firstName = firstname;
        this.lastName = lastname;
        this.dateOfBirth = dateOfBirth;
//        this.idCardFront = idCardFront;
//        this.idCardBack = idCardBack;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public LocalDateTime getDateOfBirth() {
        return dateOfBirth;
    }
}
