package com.petadoption.api.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter)
			throws Exception {

		return http
				// API sem sessão e sem cookies: não há o que um ataque CSRF
				// possa reaproveitar, já que o token vai no header Authorization.
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
						.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()

						// Catálogo público de adoção. Os padrões usam UM
						// asterisco, não dois: "/api/pets/**" pegaria também
						// sub-rotas futuras como "/api/pets/{id}/histórico", e
						// liberaria sem querer o que nascer ali embaixo.
						.requestMatchers(HttpMethod.GET, "/api/pets", "/api/pets/*").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/organizations", "/api/organizations/*").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/organizations/*/pets").permitAll()
						// A trajetória do animal é parte do catálogo: saber que
						// foi resgatado e por onde passou é o que dá confiança
						// à adoção. Já a ficha de saúde, em /health-records,
						// continua fora — detalhe clínico não é vitrine.
						.requestMatchers(HttpMethod.GET, "/api/pets/*/history").permitAll()
						// Regra final restritiva: rota nova nasce protegida. O
						// inverso — liberar por padrão e lembrar de proteger —
						// falha exatamente no dia em que alguém esquece.
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
				.build();
	}

	/**
	 * Converte o claim {@code authorities} do token em GrantedAuthority.
	 *
	 * <p>O prefixo é explicitamente vazio. Por padrão este conversor prependeria
	 * {@code SCOPE_}, e a variante por papéis prependeria {@code ROLE_} — em
	 * ambos os casos a autoridade efetiva deixaria de ser o que está escrito no
	 * banco, e a regra de autorização passaria a depender de uma concatenação
	 * invisível.
	 */
	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName(TokenService.AUTHORITIES_CLAIM);
		authorities.setAuthorityPrefix("");

		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	@Bean
	JwtDecoder jwtDecoder(JwtProperties properties) {
		return NimbusJwtDecoder.withSecretKey(properties.secretKey())
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
	}

	@Bean
	JwtEncoder jwtEncoder(JwtProperties properties) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(properties.secretKey()));
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder encoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(encoder);
		return new ProviderManager(provider);
	}
}
