package com.petadoption.api.domain;

/** The nature of one stay on the animal's timeline. */
public enum StayKind {

	/** Rescue -- usually the first record, dated before the pet was registered. */
	RESCUE,

	/** A shelter or animal welfare organization. */
	SHELTER,

	/** A foster home. */
	FOSTER,

	/** A permanent home, opened when an adoption is approved. */
	ADOPTION,

	OTHER
}
