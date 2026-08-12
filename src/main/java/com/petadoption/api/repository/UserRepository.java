package com.petadoption.api.repository;

import com.petadoption.api.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	// O e-mail é normalizado para minúsculas na escrita, então a busca exata
	// basta e continua usando o índice.
	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
}
