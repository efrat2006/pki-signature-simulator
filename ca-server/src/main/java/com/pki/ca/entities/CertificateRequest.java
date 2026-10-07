package com.pki.ca.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@Entity
@Table(name ="certificate_requests")
public class CertificateRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "signer_id", nullable = false)
    private Signer signer;

    @Column(name = "public_key", nullable = false)
    private String publicKey;

    // חתימת ה-CSR (Base64) - הוכחת ההחזקה על המפתח הפרטי
    @Column(name = "signature", nullable = false)
    private String signature;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false, columnDefinition = "status_type DEFAULT 'PENDING'")
    private StatusType requestStatus = StatusType.PENDING;

    @CreationTimestamp
    @Column(name = "request_date", nullable = false)
    private LocalDateTime requestDate;
}
