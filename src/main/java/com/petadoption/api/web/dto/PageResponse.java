package com.petadoption.api.web.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envelope de paginação próprio.
 *
 * <p>Serializar {@code Page} direto expõe a estrutura interna do Spring Data
 * como se fosse contrato da API — e ela já mudou entre versões. Um record
 * explícito é o contrato, e ele só muda quando nós decidirmos.
 */
public record PageResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {

	public static <E, T> PageResponse<T> from(Page<E> source, Function<E, T> mapper) {
		return new PageResponse<>(
				source.getContent().stream().map(mapper).toList(),
				source.getNumber(),
				source.getSize(),
				source.getTotalElements(),
				source.getTotalPages());
	}
}
