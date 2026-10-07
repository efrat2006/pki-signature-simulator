package com.pki.ca.repositories;

import com.pki.ca.entities.SecurityQuestion;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SecurityQuestionRepo extends JpaRepository<SecurityQuestion, Long> {
    List<SecurityQuestion> findByIsActiveTrue();
    Optional<SecurityQuestion> findByQuestionText(String questionText);
}


