package com.petadoption.api.web.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Our own pagination envelope.
 *
 * <p>Serialising {@code Page} directly would expose Spring Data's internal
 * structure as if it were the API contract -- and that structure has changed
 * between versions. An explicit record is the contract, and it changes only when
 * we decide it does.
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
