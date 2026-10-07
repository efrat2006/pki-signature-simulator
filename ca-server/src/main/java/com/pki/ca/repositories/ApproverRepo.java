package com.pki.ca.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.pki.ca.entities.Approver;

public interface ApproverRepo extends JpaRepository<Approver, Long> {
}
