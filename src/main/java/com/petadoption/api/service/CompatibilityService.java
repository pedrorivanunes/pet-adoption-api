package com.petadoption.api.service;

import com.petadoption.api.domain.AdopterProfile;
import com.petadoption.api.domain.Pet;
import com.petadoption.api.domain.PetStatus;
import com.petadoption.api.domain.User;
import com.petadoption.api.domain.compatibility.CompatibilityCalculator;
import com.petadoption.api.domain.compatibility.CompatibilityResult;
import com.petadoption.api.repository.PetRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * O "quero adotar": pets disponíveis ranqueados por compatibilidade com quem
 * está buscando.
 */
@Service
public class CompatibilityService {

	private final PetRepository pets;
	private final UserService users;
	private final AdopterProfileService profiles;
	private final PetAccess petAccess;
	private final CompatibilityCalculator calculator;

	public CompatibilityService(PetRepository pets, UserService users, AdopterProfileService profiles,
			PetAccess petAccess, CompatibilityCalculator calculator) {
		this.pets = pets;
		this.users = users;
		this.profiles = profiles;
		this.petAccess = petAccess;
		this.calculator = calculator;
	}

	public record Match(Pet pet, CompatibilityResult compatibility) {
	}

	/**
	 * Ranqueia os pets disponíveis para o adotante autenticado.
	 *
	 * <p>Ao contrário do score gravado na candidatura, aqui o cálculo é ao vivo:
	 * a busca precisa refletir o catálogo e as preferências de agora.
	 *
	 * <p><strong>Sobre a estratégia:</strong> os pets disponíveis são carregados
	 * e pontuados em memória. O catálogo de animais disponíveis para adoção é
	 * pequeno por natureza — centenas, não milhões — e manter a regra num único
	 * lugar em Java vale mais do que reescrevê-la em SQL para ganhar um tempo
	 * que ninguém vai sentir. Se um dia crescer a ponto de doer, o caminho é
	 * materializar o score, não espalhar a regra.
	 */
	@Transactional(readOnly = true)
	public Page<Match> matchesFor(String actorEmail, Pageable pageable) {
		User actor = users.getByEmail(actorEmail);
		AdopterProfile profile = profiles.findOf(actor).orElseThrow(() -> new ConflictException(
				"Preencha seu perfil de adotante para ver os pets mais compatíveis com você."));

		List<Match> ranked = pets.findByStatus(PetStatus.AVAILABLE, Pageable.unpaged()).getContent().stream()
				// Não faz sentido oferecer à pessoa um animal que ela mesma cuida.
				.filter(pet -> !petAccess.canManage(pet, actor))
				.map(pet -> new Match(pet, calculator.evaluate(pet, profile)))
				// Fator impeditivo elimina — é o significado dele. Aparecer no
				// fim da lista seria tratá-lo como pontuação baixa.
				.filter(match -> !match.compatibility().blocked())
				.sorted(Comparator
						.comparingInt((Match match) -> match.compatibility().score()).reversed()
						// Desempate estável, senão a mesma busca devolve ordens
						// diferentes e a paginação repete ou pula registros.
						.thenComparing(match -> match.pet().getId()))
				.toList();

		return paginate(ranked, pageable);
	}

	private Page<Match> paginate(List<Match> ranked, Pageable pageable) {
		int from = (int) Math.min(pageable.getOffset(), ranked.size());
		int to = Math.min(from + pageable.getPageSize(), ranked.size());
		return new PageImpl<>(ranked.subList(from, to), pageable, ranked.size());
	}
}
