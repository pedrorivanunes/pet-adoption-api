package com.petadoption.api.domain;

/** Natureza de uma permanência na linha do tempo do animal. */
public enum StayKind {

	/** Resgate — em geral o primeiro registro, com data anterior ao cadastro. */
	RESCUE,

	/** Abrigo ou ONG. */
	SHELTER,

	/** Lar temporário. */
	FOSTER,

	/** Lar definitivo, aberto quando uma adoção é aprovada. */
	ADOPTION,

	OTHER
}
