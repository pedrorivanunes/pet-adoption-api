package com.petadoption.api;

import org.springframework.boot.SpringApplication;

public class TestPetAdoptionApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(PetAdoptionApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
