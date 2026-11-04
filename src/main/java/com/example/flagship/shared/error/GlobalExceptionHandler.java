package com.example.flagship.shared.error;

import com.example.flagship.flag.application.DuplicateFlagKeyException;
import com.example.flagship.flag.application.FlagNotFoundException;
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

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(fieldError -> fieldError.getField()))
                .map(fieldError -> Map.of(
                        "field", fieldError.getField(),
                        "message", String.valueOf(fieldError.getDefaultMessage())))
                .toList();

        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        body.setProperty("code", ErrorCode.VALIDATION_FAILED);
        body.setProperty("errors", errors);

        return handleExceptionInternal(exception, body, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
  
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
