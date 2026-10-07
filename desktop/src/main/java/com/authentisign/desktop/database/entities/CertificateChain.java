package com.authentisign.desktop.database.entities;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "CertificateChains")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CertificateChain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "SignatureId",  nullable = false)
    private Signature signature;

    @Column(name = "CertificateId",   nullable = false)
    private Long certificateId;

    @Column(name = "Position", nullable = false)
    private int position;

    //ליצירה
    public CertificateChain( Signature signature, Long certificateId, int position) {
        this.signature = signature;
        this.certificateId = certificateId;
        this.position = position;
    }
}
