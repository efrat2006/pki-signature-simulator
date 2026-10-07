package com.pki.ca.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.*;
import lombok.Data;

@Data
@Entity
@Table (name = "certificates")
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cert_id")
    private Long id;

    @Column (name = "cert_serial_number", nullable = false,  unique = true)
    private String certSerialNumber;

    @ManyToOne
    @JoinColumn (name = "signer_id", nullable = false)
    private Signer signer;

    @Column (name = "sign_algorithm", nullable = false)
    private String signAlgorithm;

    @Column (name = "curve_name")
    private String curveName;

    @CreationTimestamp
    @Column (name = "issue_date", nullable = false)
    private LocalDateTime issueDate;

    @Column (name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    @Column (name = "public_key", nullable = false)
    private String publicKey;

    @Column (name = "cert_version", nullable = false)
    private int certVersion;

    @Enumerated(EnumType.STRING)
    @Column (name = "status", nullable = false)
    private StatusType status = StatusType.PENDING;

    // אופציונלי: בהנפקה עצמית (אימות בשאלות אבטחה) אין מאשר אנושי
    @ManyToOne
    @JoinColumn (name = "approver_id")
    private com.pki.ca.entities.Approver approver;

    // אופציונלי: לחותם לא תמיד משויך ארגון
    @ManyToOne
    @JoinColumn (name = "org_id")
    private Organization organization;

    @ManyToOne
    @JoinColumn (name = "cert_request_id", nullable = false)
    private  CertificateRequest certRequest;


    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
