package com.blockchainhandler.api.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Renders every API error as an RFC 7807 {@code application/problem+json} document.
 *
 * <p>Standard Spring MVC exceptions are handled by {@link ResponseEntityExceptionHandler}; validation errors get
 * an {@code errors} list, and every problem carries the {@code traceId} of the request so that a client report
 * can be matched with the logs. Unexpected exceptions never leak internals to the client.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String TRACE_ID = "traceId";
    private static final String VALIDATION_FAILED = "Request validation failed";

    /**
     * Handles unknown resources.
     *
     * @param ex the exception
     * @return HTTP 404 problem
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not Found");
        return withTraceId(problem);
    }

    /**
     * Handles constraint violations raised outside of Spring MVC argument resolution.
     *
     * @param ex the exception
     * @return HTTP 400 problem with the list of violations
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, VALIDATION_FAILED);
        problem.setTitle("Bad Request");
        problem.setProperty("errors", ex.getConstraintViolations().stream()
                .map(violation -> new ValidationError(leafName(violation), violation.getMessage()))
                .toList());
        return withTraceId(problem);
    }

    /**
     * Handles everything else without exposing internals.
     *
     * @param ex the exception
     * @return HTTP 500 problem
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception while processing a request", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error. Please report the traceId to find the details in the logs.");
        problem.setTitle("Internal Server Error");
        return withTraceId(problem);
    }

    /** Adds the list of invalid fields to bean validation failures of {@code @Valid} arguments. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ex.getBody();
        problem.setDetail(VALIDATION_FAILED);
        problem.setProperty("errors", ex.getBindingResult().getAllErrors().stream()
                .map(GlobalExceptionHandler::toValidationError)
                .toList());
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** Adds the list of invalid parameters to method validation failures ({@code @RequestParam}, {@code @PathVariable}). */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ex.getBody();
        problem.setDetail(VALIDATION_FAILED);
        List<ValidationError> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> toValidationError(result.getMethodParameter(), error)))
                .toList();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** Adds the trace id to the problem details produced by {@link ResponseEntityExceptionHandler}. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            withTraceId(problem);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static ProblemDetail withTraceId(ProblemDetail problem) {
        String traceId = MDC.get(TRACE_ID);
        if (traceId != null) {
            problem.setProperty(TRACE_ID, traceId);
        }
        return problem;
    }

    private static ValidationError toValidationError(ObjectError error) {
        String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
        return new ValidationError(field, error.getDefaultMessage());
    }

    private static ValidationError toValidationError(MethodParameter parameter, MessageSourceResolvable error) {
        String field = error instanceof FieldError fieldError ? fieldError.getField() : parameter.getParameterName();
        return new ValidationError(field, error.getDefaultMessage());
    }

    private static String leafName(ConstraintViolation<?> violation) {
        String name = null;
        for (Path.Node node : violation.getPropertyPath()) {
            name = node.getName();
        }
        return name;
    }
}
