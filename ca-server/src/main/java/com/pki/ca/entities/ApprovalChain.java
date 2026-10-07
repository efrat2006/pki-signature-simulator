package com.pki.ca.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.*;
import lombok.Data;

@Data
@Entity
@Table (name = "approver_hierarchy")
public class ApprovalChain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column (name = "chain_id")
    private Long id;

    @ManyToOne
    @JoinColumn (name = "certificate_id", nullable = false, unique = true)
    private Certificate certificate;

    @ManyToOne
    @JoinColumn (name = "issuer_certificate_id")
    private Certificate issuerCertificate;

    @CreationTimestamp
    @Column (name = "created_at", nullable = false)
    private LocalDateTime createdAt;


}
