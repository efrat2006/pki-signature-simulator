package com.pki.ca.repositories;

import com.pki.ca.entities.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;

import java.security.cert.Certificate;
import java.util.List;

public interface CertRequestRepo extends JpaRepository<CertificateRequest, Long> {
    /**מאתרת את כל התעודות שנחתמו על ידי חותם בעל מזהה מסוים**/
    List<Certificate> findBySignerId(Long signerId);
    /*** מאתרת את כל הבקשות לפי סטטוס מוגדר*/
    List<CertificateRequest> findByRequestStatus(StatusType status);
}

