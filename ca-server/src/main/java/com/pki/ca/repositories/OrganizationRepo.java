package com.pki.ca.repositories;

import com.pki.ca.entities.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepo extends JpaRepository<Organization,Long> {
}
