package com.petadoption.api.service;

/** Lançada quando o usuário procurado não existe. */
public class UserNotFoundException extends RuntimeException {

	public UserNotFoundException(String email) {
		super("Usuário não encontrado: " + email);
	}
}
