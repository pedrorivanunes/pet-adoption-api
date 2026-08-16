package com.petadoption.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An integration test against a real PostgreSQL via Testcontainers.
 *
 * <p>The JWT secret is supplied here and nowhere else: the application defines
 * no default, so forgetting to configure it brings the boot down. This is the
 * point where the test environment takes that responsibility explicitly.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
		"app.security.jwt.secret=test-only-secret-with-more-than-32-characters",
		"app.security.jwt.ttl=PT1H"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
