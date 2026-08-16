package com.petadoption.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * A shelter, NGO or rescue group that lists pets for adoption.
 *
 * <p>There is deliberately no "admin user" reference here. Who may act on behalf
 * of the organization is defined exclusively by {@link OrganizationMembership}
 * -- a single source for that fact.
 */
@Entity
@Table(name = "organizations")
@Getter
@Setter
public class Organization {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	private String description;

	private String email;

	private String phone;

	private String address;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;
}
