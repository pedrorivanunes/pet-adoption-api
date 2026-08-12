package com.petadoption.api.service;

/** Lançada quando já existe conta para o e-mail informado no cadastro. */
public class EmailAlreadyUsedException extends ConflictException {

	public EmailAlreadyUsedException(String email) {
		super("Já existe uma conta com o e-mail " + email);
	}
}
