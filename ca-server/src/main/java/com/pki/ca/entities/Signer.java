package com.pki.ca.entities;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.*;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@Entity
@Table(name = "signers")
public class Signer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column (name = "signer_id")
    private Long id;

    @Column (name = "first_name", nullable = false)
    private String firstName;

    @Column (name = "last_name", nullable = false)
    private String lastName;

    @Column (name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Email
    @NotBlank
    @Column (name = "email", nullable = false, unique = true)
    private String email;

    @Column (name = "id_card_front", nullable = false)
    private byte[] idCardFront;

    @Column (name = "id_card_back", nullable = false)
    private byte[] idCardBack;

    @ManyToOne
    @JoinColumn(name = "org_id")
    private Organization organization;

}
