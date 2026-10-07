package com.authentisign.desktop.database.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;


@Entity
@Table(name = "Proofs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Proof {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "SignatureId", nullable = false)
    private Signature signature;

    @ManyToOne
    @JoinColumn(name = "ChainId",  nullable = false)
    private CertificateChain chain;

    @Column(name = "ProofType",  nullable = false, columnDefinition = "NVARCHAR(50)")
    private String proofType;

    @Column(name = "ProofValue", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String proofValue;

    @Column(name = "ProducedAt", nullable = false)
    private LocalDateTime producedAt;


    //ליצירה
    public Proof(Signature signature, CertificateChain chain, String proofType, String proofValue) {
        this.signature = signature;
        this.chain = chain;
        this.proofType = proofType;
        this.proofValue = proofValue;
        this.producedAt = LocalDateTime.now();
    }
}
