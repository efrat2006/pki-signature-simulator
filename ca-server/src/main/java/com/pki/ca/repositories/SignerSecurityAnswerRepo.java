package com.pki.ca.repositories;

import com.pki.ca.entities.SignerSecurityAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public interface SignerSecurityAnswerRepo extends JpaRepository<SignerSecurityAnswer, Long> {
    List<SignerSecurityAnswer> findBySigner_Id(Long signerId);
}
