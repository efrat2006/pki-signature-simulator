package com.pki.ca.entities;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "security_questions")
public class SecurityQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column (name = "question_id")
    private Long id;

    @Column (name = "question_text", nullable = false)
    private String questionText;

    @Column (name = "is_active", nullable = false)
    private boolean isActive;
}
