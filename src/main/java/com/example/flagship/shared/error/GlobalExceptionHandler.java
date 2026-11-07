package com.example.flagship.shared.error;

import com.example.flagship.flag.application.ConfigurationVersionConflictException;
import com.example.flagship.flag.application.DuplicateFlagKeyException;
import com.example.flagship.flag.application.EnvironmentNotFoundException;
import com.example.flagship.flag.application.FlagArchivedException;
import com.example.flagship.flag.application.FlagNotFoundException;
import com.example.flagship.flag.domain.InvalidConfigurationException;
import com.fasterxml.jackson.databind.exc.PropertyBindingException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(FlagNotFoundException.class)
    ProblemDetail flagNotFound(FlagNotFoundException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, ErrorCode.FLAG_NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(DuplicateFlagKeyException.class)
    ProblemDetail duplicateKey(DuplicateFlagKeyException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ErrorCode.FLAG_KEY_CONFLICT, e.getMessage(), request);
    }

    @ExceptionHandler(EnvironmentNotFoundException.class)
    ProblemDetail environmentNotFound(EnvironmentNotFoundException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, ErrorCode.ENVIRONMENT_NOT_FOUND, e.getMessage(), request);
    }

    @ExceptionHandler(FlagArchivedException.class)
    ProblemDetail flagArchived(FlagArchivedException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ErrorCode.FLAG_ARCHIVED, e.getMessage(), request);
    }

    @ExceptionHandler(ConfigurationVersionConflictException.class)
    ProblemDetail versionConflict(ConfigurationVersionConflictException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ErrorCode.CONFIGURATION_VERSION_CONFLICT, e.getMessage(), request);
    }

    @ExceptionHandler(InvalidConfigurationException.class)
    ProblemDetail invalidConfiguration(InvalidConfigurationException e, HttpServletRequest request) {
        ProblemDetail problem = validationProblem(e.getViolations().stream()
                .map(v -> fieldError(v.field(), v.message()))
                .toList());
        problem.setInstance(URI.create(request.getRequestURI()));

        return problem;
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalidRequest(InvalidRequestException e, HttpServletRequest request) {
        ProblemDetail problem = validationProblem(e.getProblems().stream()
                .map(p -> fieldError(p.field(), p.message()))
                .toList());
        problem.setInstance(URI.create(request.getRequestURI()));

        return problem;
    }

    @ExceptionHandler(MalformedRequestException.class)
    ProblemDetail malformed(MalformedRequestException e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST, e.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = validationProblem(exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(fieldError -> fieldError.getField()))
                .map(fieldError -> fieldError(fieldError.getField(), String.valueOf(fieldError.getDefaultMessage())))
                .toList());

        return handleExceptionInternal(exception, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Well-formed JSON naming a field the endpoint does not accept (unknown or server-managed)
        // is a validation problem about that field, not unreadable input.
        if (exception.getCause() instanceof PropertyBindingException unknown) {
            ProblemDetail body = validationProblem(List.of(
                    fieldError(unknown.getPropertyName(), "is not a writable field for this request")));

            return handleExceptionInternal(exception, body, headers, HttpStatus.BAD_REQUEST, request);
        }

        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Request body is missing or is not valid JSON for this endpoint");
        body.setProperty("code", ErrorCode.MALFORMED_REQUEST);

        return handleExceptionInternal(exception, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
                problem.setProperty("code", defaultCode(status));
            }
            if (problem.getInstance() == null && request instanceof ServletWebRequest swr) {
                problem.setInstance(URI.create(swr.getRequest().getRequestURI()));
            }
        }

        return super.handleExceptionInternal(e, body, headers, status, request);
    }

    private static ProblemDetail validationProblem(List<Map<String, String>> errors) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        body.setProperty("code", ErrorCode.VALIDATION_FAILED);
        body.setProperty("errors", errors);

        return body;
    }

    private static Map<String, String> fieldError(String field, String message) {
        return Map.of("field", field, "message", message);
    }

    private static ProblemDetail problem(HttpStatus status, ErrorCode code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        problem.setInstance(URI.create(request.getRequestURI()));
        
        return problem;
    }

    private static ErrorCode defaultCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 400 -> ErrorCode.BAD_REQUEST;
            default -> status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.BAD_REQUEST;
        };
    }
}
