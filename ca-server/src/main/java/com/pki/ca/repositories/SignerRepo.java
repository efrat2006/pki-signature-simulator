package com.pki.ca.repositories;

import com.pki.ca.entities.Signer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SignerRepo extends JpaRepository<Signer, Long> {
    /**שולפת חותם ספציפי על פי כתובת האימייל שלו**/
    Optional<Signer> findByEmail(String email);
    /**מאתרת את כל החותמים המשוכיים לארגון ספציפי**/
    List<Signer> findByOrganizationId(Long organizationId);
}