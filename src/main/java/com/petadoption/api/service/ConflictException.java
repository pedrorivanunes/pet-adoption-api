package com.petadoption.api.service;

/**
 * A business rule violated by a conflict with the current state -- the request
 * is well-formed, but cannot be served right now. Becomes a 409 at the web edge.
 */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}
}
