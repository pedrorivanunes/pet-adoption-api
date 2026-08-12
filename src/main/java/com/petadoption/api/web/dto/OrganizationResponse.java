package com.petadoption.api.web.dto;

import com.petadoption.api.domain.Organization;

public record OrganizationResponse(
		Long id,
		String name,
		String description,
		String email,
		String phone,
		String address) {

	public static OrganizationResponse from(Organization organization) {
		return new OrganizationResponse(
				organization.getId(),
				organization.getName(),
				organization.getDescription(),
				organization.getEmail(),
				organization.getPhone(),
				organization.getAddress());
	}
}
