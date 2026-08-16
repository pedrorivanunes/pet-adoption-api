package com.petadoption.api.service;

/** Thrown when the requested user does not exist. */
public class UserNotFoundException extends NotFoundException {

	public UserNotFoundException(String email) {
		super("User not found: " + email);
	}
}
