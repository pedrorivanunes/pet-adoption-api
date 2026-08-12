package com.petadoption.api.service;

/** Lançada quando o usuário procurado não existe. */
public class UserNotFoundException extends NotFoundException {

	public UserNotFoundException(String email) {
		super("Usuário não encontrado: " + email);
	}
}
