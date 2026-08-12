package com.petadoption.api.web.error;

import com.petadoption.api.service.EmailAlreadyUsedException;
import com.petadoption.api.service.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduz exceções de negócio em respostas RFC 7807 (ProblemDetail).
 *
 * <p><strong>Não existe aqui um handler para {@code Exception}.</strong> Um
 * catch-all parece defensivo, mas o resolvedor de {@code @ControllerAdvice}
 * roda antes dos resolvedores padrão do Spring: ele engoliria o 401 de
 * credencial inválida, o 403 de acesso negado, o 400 de JSON malformado e o 405
 * de método não suportado, devolvendo 500 para todos. Erros de framework ficam
 * com o Spring, que já sabe o status correto de cada um
 * ({@code spring.mvc.problemdetails.enabled} os formata como ProblemDetail).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(EmailAlreadyUsedException.class)
	ProblemDetail handleEmailAlreadyUsed(EmailAlreadyUsedException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
		problem.setTitle("E-mail já cadastrado");
		return problem;
	}

	@ExceptionHandler(UserNotFoundException.class)
	ProblemDetail handleUserNotFound(UserNotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
		problem.setTitle("Recurso não encontrado");
		return problem;
	}

	/**
	 * Credencial inválida no login. A mensagem é deliberadamente genérica: dizer
	 * "e-mail não existe" versus "senha errada" entrega ao atacante quais contas
	 * existem.
	 */
	@ExceptionHandler(AuthenticationException.class)
	ProblemDetail handleAuthentication(AuthenticationException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
		problem.setTitle("Não autenticado");
		return problem;
	}

	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail handleAccessDenied(AccessDeniedException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.FORBIDDEN, "Você não tem permissão para esta operação");
		problem.setTitle("Acesso negado");
		return problem;
	}
}
