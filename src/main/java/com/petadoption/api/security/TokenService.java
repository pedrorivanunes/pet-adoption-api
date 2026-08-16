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

/** Issues the access tokens. Validation is the resource server's job. */
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

		// A space separator follows the OAuth2 "scope" claim convention, which
		// is the format JwtGrantedAuthoritiesConverter already knows how to read.
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

		// The algorithm has to be explicit in the header: without it the encoder
		// assumes an asymmetric algorithm and finds no key to sign with. It has
		// to match the MacAlgorithm configured on the decoder.
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

		String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(value, properties.ttl().toSeconds());
	}

	public record IssuedToken(String value, long expiresInSeconds) {
	}
}
