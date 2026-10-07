package com.authentisign.desktop.database.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;


@Entity
@Table(name = "Signatures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Signature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "DocumentId",  nullable = false)
    private Long documentId;

    @Column(name = "UserId",  nullable = false)
    private Long userId;

    @Column(name = "CertificateToken",  nullable = false)
    private String certificateToken;

    @Column(name = "Format",  nullable = false, length = 20)
    private String format;

    @Column(name = "Value",  nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String signatureValue;

    @Column(name = "SignedAt",  nullable = false)
    private LocalDateTime signedAt;

    //יצירה
    public Signature(Long documentId, Long userId, String certificateToken, String format, String signatureValue, LocalDateTime signedAt) {
        this.documentId = documentId;
        this.userId = userId;
        this.certificateToken = certificateToken;
        this.format = format;
        this.signatureValue = signatureValue;
        this.signedAt = signedAt;
    }
}
