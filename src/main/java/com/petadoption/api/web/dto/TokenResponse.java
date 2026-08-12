package com.petadoption.api.web.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

	public static TokenResponse bearer(String value, long expiresInSeconds) {
		return new TokenResponse(value, "Bearer", expiresInSeconds);
	}
}
