package com.petadoption.api.service;

/** Recurso solicitado não existe. Vira 404 na borda web. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}

	public static NotFoundException of(String resource, Object id) {
		return new NotFoundException(resource + " não encontrado(a): " + id);
	}
}
