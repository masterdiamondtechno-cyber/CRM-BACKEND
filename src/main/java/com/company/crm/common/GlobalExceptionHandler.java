package com.company.crm.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import java.sql.SQLException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErrorBody(Instant timestamp, int status, String errorCode, String message,
                            Map<String, String> validationErrors, String path) { }

    private ResponseEntity<ErrorBody> body(HttpStatus status, String code, String message,
                                           Map<String, String> fields, HttpServletRequest req) {
        return ResponseEntity.status(status).body(new ErrorBody(Instant.now(), status.value(), code,
                message, fields, req.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> invalid(MethodArgumentNotValidException e, HttpServletRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(x -> errors.putIfAbsent(x.getField(), x.getDefaultMessage()));
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", errors, req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorBody> constraint(ConstraintViolationException e, HttpServletRequest req) {
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request parameters are invalid", Map.of(), req);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ErrorBody> status(ResponseStatusException e, HttpServletRequest req) {
        HttpStatus s = HttpStatus.valueOf(e.getStatusCode().value());
        String message = s.is4xxClientError() ? e.getReason() : "Request failed";
        return body(s, s.name(), message == null ? s.getReasonPhrase() : message, Map.of(), req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorBody> forbidden(AccessDeniedException e, HttpServletRequest req) {
        return body(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied", Map.of(), req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorBody> conflict(DataIntegrityViolationException e, HttpServletRequest req) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String sqlState = root instanceof SQLException sql ? sql.getSQLState() : "unknown";
        log.warn("Data integrity conflict for {} {} (cause={}, sqlState={})",
                req.getMethod(), req.getRequestURI(), root.getClass().getSimpleName(), sqlState);
        return body(HttpStatus.CONFLICT, "DATA_CONFLICT", "Resource conflicts with existing data", Map.of(), req);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> unexpected(Exception e, HttpServletRequest req) {
        // Keep internal diagnostics in server logs only; the HTTP response stays generic.
        log.error("Unhandled API exception for {} {}", req.getMethod(), req.getRequestURI(), e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", Map.of(), req);
    }
}
