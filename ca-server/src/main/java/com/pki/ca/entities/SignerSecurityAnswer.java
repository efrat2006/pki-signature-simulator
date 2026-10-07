package com.pki.ca.entities;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "signer_security_answers")
public class SignerSecurityAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "security_answers_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signer_id", nullable = false)
    private Signer signer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private SecurityQuestion question;

    @Column(name = "answer_hash", nullable = false)
    private String answerHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public SignerSecurityAnswer() {}

    public SignerSecurityAnswer(Signer signer, SecurityQuestion question, String answerHash) {
        this.signer = signer;
        this.question = question;
        this.answerHash = answerHash;
    }

}
