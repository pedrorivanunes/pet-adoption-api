package com.petadoption.api.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.stream.Collectors;

/** Emite os tokens de acesso. A validação fica com o resource server. */
@Service
public class TokenService {

	static final String AUTHORITIES_CLAIM = "authorities";

	private static final String ISSUER = "pet-adoption-api";

	private final JwtEncoder encoder;
	private final JwtProperties properties;

	public TokenService(JwtEncoder encoder, JwtProperties properties) {
		this.encoder = encoder;
		this.properties = properties;
	}

	public IssuedToken issue(UserDetails user) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(properties.ttl());

		// Espaço como separador segue a convenção do claim "scope" do OAuth2,
		// que é o formato que o JwtGrantedAuthoritiesConverter já sabe ler.
		String authorities = user.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.collect(Collectors.joining(" "));

		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(ISSUER)
				.issuedAt(now)
				.expiresAt(expiresAt)
				.subject(user.getUsername())
				.claim(AUTHORITIES_CLAIM, authorities)
				.build();

		// O algoritmo precisa ir explícito no header: sem ele o encoder assume
		// um algoritmo assimétrico e não encontra chave para assinar. Tem que
		// casar com o MacAlgorithm configurado no decoder.
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

		String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(value, properties.ttl().toSeconds());
	}

	public record IssuedToken(String value, long expiresInSeconds) {
	}
}
