package com.petadoption.api.web.dto;

import com.petadoption.api.domain.OrgMemberRole;
import com.petadoption.api.domain.OrganizationMembership;

import java.time.OffsetDateTime;

public record MemberResponse(
		Long userId,
		String name,
		String email,
		OrgMemberRole role,
		OffsetDateTime memberSince) {

	public static MemberResponse from(OrganizationMembership membership) {
		return new MemberResponse(
				membership.getUser().getId(),
				membership.getUser().getName(),
				membership.getUser().getEmail(),
				membership.getMemberRole(),
				membership.getCreatedAt());
	}
}
