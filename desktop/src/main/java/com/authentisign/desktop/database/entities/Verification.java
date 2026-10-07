package com.authentisign.desktop.database.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;


@Entity
@Table(name = "Verifications")
@Getter
@Setter
@NoArgsConstructor      //מייצר בנאי ריק - במקום לכתוב אותו ידנית
@AllArgsConstructor
public class Verification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "SignatureId", nullable = false)
    private Signature signature;

    @Enumerated(EnumType.STRING)
    @Column(name = "VerifyResult",  nullable = false, length = 20)
    private VerificationResult verificationResult;

    @Column(name = "VerifiedAt", nullable = false)
    private LocalDateTime verifiedAt;

    @Column(name = "Message", columnDefinition = "NVARCHAR(MAX)")
    private String statusMessage;

    //יצירה
    public Verification(Signature signature, VerificationResult verificationResult, String statusMessage) {
        this.signature = signature;
        this.verificationResult = verificationResult;
        this.verifiedAt = LocalDateTime.now();
        this.statusMessage = statusMessage;
    }
}
