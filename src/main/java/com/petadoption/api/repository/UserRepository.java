package com.petadoption.api.repository;

import com.petadoption.api.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	// Email is normalised to lower case on write, so an exact lookup is enough
	// and still uses the index.
	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
}
