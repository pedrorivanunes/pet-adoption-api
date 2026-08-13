package com.petadoption.api.domain.compatibility;

import java.util.List;

/**
 * Resultado de uma avaliação de compatibilidade entre pet e adotante.
 *
 * <p>Carrega a decomposição, e não só o número. Um ranking que diz "87" e nada
 * mais é impossível de conferir e impossível de explicar a quem está adotando;
 * com os fatores, dá para mostrar <em>por que</em> aquele animal apareceu no
 * topo — e dá para depurar quando aparecer no lugar errado.
 *
 * @param score     soma dos pontos dos fatores
 * @param blocked   há fator impeditivo
 * @param factors   o que somou ou subtraiu, e quanto
 * @param blockers  motivos da eliminação, quando houver
 */
public record CompatibilityResult(
		int score,
		boolean blocked,
		List<Factor> factors,
		List<String> blockers) {

	/**
	 * @param category rótulo estável do fator (ex.: {@code SPECIES})
	 * @param points   contribuição para o score
	 * @param detail   explicação legível
	 */
	public record Factor(String category, int points, String detail) {
	}

	public CompatibilityResult {
		factors = List.copyOf(factors);
		blockers = List.copyOf(blockers);
	}
}
