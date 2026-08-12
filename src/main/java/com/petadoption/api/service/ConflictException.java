package com.petadoption.api.service;

/**
 * Regra de negócio violada por conflito com o estado atual — o pedido é válido
 * em forma, mas não pode ser atendido agora. Vira 409 na borda web.
 */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}
}
