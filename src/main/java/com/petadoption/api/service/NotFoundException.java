package com.petadoption.api.service;

/** The requested resource does not exist. Becomes a 404 at the web edge. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}

	public static NotFoundException of(String resource, Object id) {
		return new NotFoundException(resource + " not found: " + id);
	}
}
