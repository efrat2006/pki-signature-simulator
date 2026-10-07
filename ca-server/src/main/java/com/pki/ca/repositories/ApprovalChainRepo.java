package com.pki.ca.repositories;

import com.pki.ca.entities.ApprovalChain;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalChainRepo extends JpaRepository<ApprovalChain, Long> {
}
