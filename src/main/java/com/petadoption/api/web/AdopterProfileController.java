package com.petadoption.api.web;

import com.petadoption.api.service.AdopterProfileService;
import com.petadoption.api.web.dto.AdopterProfileRequest;
import com.petadoption.api.web.dto.AdopterProfileResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Perfil de adotante da pessoa autenticada.
 *
 * <p>Fica sob {@code /api/users/me/} porque é um recurso do próprio usuário, e
 * não uma coleção navegável: não existe rota para ler o perfil alheio. Essa é
 * informação sensível — moradia, filhos, quantas pessoas moram na casa — que
 * quem administra um pet só precisa ver no contexto de uma candidatura.
 */
@RestController
@RequestMapping("/api/users/me/adopter-profile")
public class AdopterProfileController {

	private final AdopterProfileService profiles;

	public AdopterProfileController(AdopterProfileService profiles) {
		this.profiles = profiles;
	}

	@GetMapping
	public AdopterProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
		return AdopterProfileResponse.from(profiles.getOf(jwt.getSubject()));
	}

	/** Idempotente: cria na primeira chamada, atualiza nas seguintes. */
	@PutMapping
	public AdopterProfileResponse save(@Valid @RequestBody AdopterProfileRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		return AdopterProfileResponse.from(profiles.save(request.toData(), jwt.getSubject()));
	}
}
