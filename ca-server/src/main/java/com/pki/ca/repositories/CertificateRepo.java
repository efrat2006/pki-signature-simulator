package com.pki.ca.repositories;

import com.pki.ca.entities.Certificate;
import  com.pki.ca.entities.StatusType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface CertificateRepo extends org.springframework.data.jpa.repository.JpaRepository<com.pki.ca.entities.Certificate, Long> {    /**מאתרת את כל התעודות השייכות לחותם מסוים לפי מזהה החותם**/
    List<Certificate> findBySignerId(Long signerId);
    /**מאתרת את כל התעודות שיש להן סטטוס מסוים**/
    List<Certificate> findByStatus(StatusType status);

    Optional<Certificate> findBycertSerialNumber(String certSerialNumber);
}
