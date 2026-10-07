package com.pki.ca.entities;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table (name = "organizations")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "org_id")
    private Long id;

    @Column (name = "serial_num", nullable = false)
    private int serialNum;

    @Column (name = "org_name", nullable = false)
    private String orgName;
}
