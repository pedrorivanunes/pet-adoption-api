package com.petadoption.api.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * JWT configuration, typed and validated.
 *
 * <p>Being a validated {@code @ConfigurationProperties} is not cosmetic: a
 * misspelled property key, or a missing secret, brings the application down at
 * boot with an explicit message. The common alternative -- reading the property
 * with {@code @Value} and an inline default -- fails silently when the key does
 * not match, and the application goes on signing tokens with the default without
 * anyone noticing.
 *
 * @param secret HMAC secret. HS256 requires a 256-bit key, hence the 32-character
 *               minimum.
 * @param ttl    lifetime of the issued token.
 */
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(

		@NotBlank(message = "app.security.jwt.secret is required (set the JWT_SECRET variable)")
		@Size(min = 32, message = "app.security.jwt.secret needs at least 32 characters for HS256")
		String secret,

		Duration ttl) {

	public JwtProperties {
		if (ttl == null) {
			ttl = Duration.ofHours(4);
		}
	}

	public SecretKey secretKey() {
		return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}
}
