package com.petadoption.api.service;

/** Thrown when an account already exists for the email given at registration. */
public class EmailAlreadyUsedException extends ConflictException {

	public EmailAlreadyUsedException(String email) {
		super("An account already exists for the email " + email);
	}
}
