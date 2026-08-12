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
 * Configuração do JWT, tipada e validada.
 *
 * <p>Ser um {@code @ConfigurationProperties} validado não é detalhe estético:
 * uma chave de propriedade escrita errada, ou um segredo ausente, derruba a
 * aplicação no boot com mensagem explícita. A alternativa comum — ler a
 * propriedade com {@code @Value} e um valor padrão embutido — falha em silêncio
 * quando a chave não bate, e a aplicação passa a assinar tokens com o padrão
 * sem ninguém perceber.
 *
 * @param secret segredo HMAC. HS256 exige chave de 256 bits, daí o mínimo de
 *               32 caracteres.
 * @param ttl    validade do token emitido.
 */
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(

		@NotBlank(message = "app.security.jwt.secret é obrigatório (defina a variável JWT_SECRET)")
		@Size(min = 32, message = "app.security.jwt.secret precisa de ao menos 32 caracteres para HS256")
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
