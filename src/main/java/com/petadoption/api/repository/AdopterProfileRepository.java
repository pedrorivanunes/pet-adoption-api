package com.petadoption.api.repository;

import com.petadoption.api.domain.AdopterProfile;
import org.springframework.data.jpa.repository.JpaRepository;

/** The primary key is the user id: the profile shares its identity with the user. */
public interface AdopterProfileRepository extends JpaRepository<AdopterProfile, Long> {
}
