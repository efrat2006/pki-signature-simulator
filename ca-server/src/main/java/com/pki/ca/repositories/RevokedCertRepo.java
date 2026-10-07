package com.pki.ca.repositories;

import com.pki.ca.entities.Certificate;
import com.pki.ca.entities.RevokedCertificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RevokedCertRepo extends JpaRepository<RevokedCertificate, Long> {

    /**מאתרת תעודה שבוטלה מתוך רשימת הביטולים ע"פ המספר הסידורי - הייחודי של התעודה**/
    Optional<RevokedCertificate> findByCertSerialNumber(Certificate certSerialNumber);
}
