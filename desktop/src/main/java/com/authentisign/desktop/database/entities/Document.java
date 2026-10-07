package com.authentisign.desktop.database.entities;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "Documents")
@Getter
@Setter
@NoArgsConstructor      //מייצר בנאי ריק - במקום לכתוב אותו ידנית
@AllArgsConstructor
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "Name", nullable = false, columnDefinition = "NVARCHAR(255)")
    private String name;

    @Column(name = "Type", nullable = false)
    private String type;

    @Column(name = "HashAlgorithm", nullable = false)
    private String hashAlgorithm;

    @Column(name = "HashValue", nullable = false)
    private String hashValue;

    @Column(name = "Status")
    private String status;

    @Column(name = "UploaderId", nullable = false)
    private Long uploaderId;

    @Column(name = "UploadedAt")
    private LocalDateTime uploadedAt;

    //ליצירת מסמך חדש
    public Document(String name, String type, String hashAlgorithm, String hashValue, String status, Long uploaderId, LocalDateTime uploadedAt) {
        this.name = name;
        this.type = type;
        this.hashAlgorithm = hashAlgorithm;
        this.hashValue = hashValue;
        this.status = "PENDING";
        this.uploaderId = uploaderId;
        this.uploadedAt = uploadedAt;
    }
}
