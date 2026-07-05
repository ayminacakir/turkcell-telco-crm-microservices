package com.turkcell.notification_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        return enrich(pd, "https://telco.example/errors/not-found", "Resource not found", request);
    }

    @ExceptionHandler(DuplicateCodeException.class)
    public ProblemDetail handleDuplicate(DuplicateCodeException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        return enrich(pd, "https://telco.example/errors/duplicate-code", "Duplicate code", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setProperty("errors", fieldErrors);
        return enrich(pd, "https://telco.example/errors/validation-error", "Validation error", request);
    }

    private ProblemDetail enrich(ProblemDetail pd, String type, String title, HttpServletRequest request) {
        pd.setType(URI.create(type));
        pd.setTitle(title);
        pd.setInstance(URI.create(request.getRequestURI()));
        String correlationId = request.getHeader("Correlation-Id");
        pd.setProperty("correlationId", correlationId != null ? correlationId : UUID.randomUUID().toString());
        return pd;
    }
}
