package com.petadoption.api.web.error;

import com.petadoption.api.service.ConflictException;
import com.petadoption.api.service.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates business exceptions into RFC 7807 responses (ProblemDetail).
 *
 * <p><strong>There is deliberately no handler for {@code Exception} here.</strong>
 * A catch-all looks defensive, but the {@code @ControllerAdvice} resolver runs
 * ahead of Spring's default resolvers: it would swallow the 401 for bad
 * credentials, the 403 for denied access, the 400 for malformed JSON and the 405
 * for an unsupported method, returning 500 for all of them. Framework errors stay
 * with Spring, which already knows the right status for each
 * ({@code spring.mvc.problemdetails.enabled} formats them as ProblemDetail).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(ConflictException.class)
	ProblemDetail handleConflict(ConflictException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
		problem.setTitle("Conflito com o estado atual");
		return problem;
	}

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail handleNotFound(NotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
		problem.setTitle("Resource not found");
		return problem;
	}

	/**
	 * Bad credentials at login. The message is deliberately generic: saying
	 * "email does not exist" versus "wrong password" tells an attacker which
	 * accounts exist.
	 */
	@ExceptionHandler(AuthenticationException.class)
	ProblemDetail handleAuthentication(AuthenticationException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.UNAUTHORIZED, "Invalid credentials");
		problem.setTitle("Not authenticated");
		return problem;
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail handleAccessDenied(AccessDeniedException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.FORBIDDEN, "You do not have permission for this operation");
		problem.setTitle("Acesso negado");
		return problem;
	}
}
