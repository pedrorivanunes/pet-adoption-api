package com.petadoption.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Teste de integração com PostgreSQL real via Testcontainers.
 *
 * <p>O segredo do JWT é fornecido aqui e em nenhum outro lugar: a aplicação não
 * define valor padrão, então esquecer de configurá-lo derruba o boot. Este é o
 * ponto onde o ambiente de teste assume essa responsabilidade explicitamente.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
		"app.security.jwt.secret=segredo-exclusivo-de-teste-com-mais-de-32-caracteres",
		"app.security.jwt.ttl=PT1H"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
