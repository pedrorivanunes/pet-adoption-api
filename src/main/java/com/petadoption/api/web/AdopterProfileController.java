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
 * The authenticated person's adopter profile.
 *
 * <p>It lives under {@code /api/users/me/} because it is a resource of the user
 * themselves, not a browsable collection: there is no route to read someone
 * else's profile. This is sensitive information -- housing, children, how many
 * people live in the home -- that whoever manages a pet only needs to see in the
 * context of an application.
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

	/** Idempotent: creates on the first call, updates on the ones after. */
	@PutMapping
	public AdopterProfileResponse save(@Valid @RequestBody AdopterProfileRequest request,
			@AuthenticationPrincipal Jwt jwt) {

		return AdopterProfileResponse.from(profiles.save(request.toData(), jwt.getSubject()));
	}
}
