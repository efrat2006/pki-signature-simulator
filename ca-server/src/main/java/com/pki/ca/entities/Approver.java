package com.pki.ca.entities;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table (name = "approvers")
public class Approver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column (name = "approver_id")
    private Long id;

    @Column (name = "approver_name", nullable = false)
    private String approverName;

    @Column (name = "approver_sig", nullable = false)
    private String approverSig;

    @Column (name = "approver_cert", nullable = false)
    private  String approverCert;

}
