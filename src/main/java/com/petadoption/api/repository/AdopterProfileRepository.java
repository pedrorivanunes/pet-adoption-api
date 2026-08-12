package com.petadoption.api.repository;

import com.petadoption.api.domain.AdopterProfile;
import org.springframework.data.jpa.repository.JpaRepository;

/** Chave primária é o id do usuário: o perfil compartilha identidade com ele. */
public interface AdopterProfileRepository extends JpaRepository<AdopterProfile, Long> {
}
