package com.pki.ca.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.*;
import lombok.Data;

@Data
@Entity
@Table (name = "revoked_certificates")
@NoArgsConstructor
@AllArgsConstructor
public class RevokedCertificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "revocation_id")
    private Long id;

    @ManyToOne
    @JoinColumn (name = "cert_serial_number", referencedColumnName = "cert_serial_number", nullable = false, unique = true)
    private com.pki.ca.entities.Certificate certSerialNumber;

    @CreationTimestamp
    @Column (name = "revocation_date", nullable = false)
    private LocalDateTime revocationDate;

    @Column (name = "revocation_reason")
    private String revocationReason;
}
