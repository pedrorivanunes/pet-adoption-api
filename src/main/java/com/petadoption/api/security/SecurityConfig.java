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
				// A sessionless, cookieless API: there is nothing for a CSRF
				// attack to replay, since the token travels in the Authorization
				// header.
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
						.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()

						// The public adoption catalogue. These patterns use ONE
						// asterisk, not two: "/api/pets/**" would also match
						// future sub-routes such as "/api/pets/{id}/history",
						// and would expose whatever gets added down there.
						.requestMatchers(HttpMethod.GET, "/api/pets", "/api/pets/*").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/organizations", "/api/organizations/*").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/organizations/*/pets").permitAll()
						// The animal's history is part of the catalogue: knowing
						// it was rescued and where it has been is what gives an
						// adopter confidence. The health record, at
						// /health-records, stays out -- clinical detail is not
						// a shop window.
						.requestMatchers(HttpMethod.GET, "/api/pets/*/history").permitAll()
						// A restrictive final rule: a new route is born
						// protected. The inverse -- open by default and remember
						// to protect -- fails on exactly the day someone forgets.
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
				.build();
	}

	/**
	 * Turns the token's {@code authorities} claim into GrantedAuthority values.
	 *
	 * <p>The prefix is explicitly empty. By default this converter would prepend
	 * {@code SCOPE_}, and the role-based variant would prepend {@code ROLE_} --
	 * in either case the effective authority would stop being what is written in
	 * the database, and the authorization rules would come to depend on an
	 * invisible concatenation.
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
