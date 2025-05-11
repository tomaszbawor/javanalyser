package sh.tbawor.javanalyser.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler that provides consistent error responses
 * for all application exceptions.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles ParsingException instances
     */
    @ExceptionHandler(ParsingException.class)
    public ResponseEntity<Object> handleParsingException(
            ParsingException ex, WebRequest request) {
        log.error("Parsing error occurred", ex);
        return buildErrorResponse(ex, "Parsing error occurred", HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /**
     * Handles EmbeddingException instances
     */
    @ExceptionHandler(EmbeddingException.class)
    public ResponseEntity<Object> handleEmbeddingException(
            EmbeddingException ex, WebRequest request) {
        log.error("Embedding error occurred", ex);
        return buildErrorResponse(ex, "Embedding error occurred", HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /**
     * Handles QueryException instances
     */
    @ExceptionHandler(QueryException.class)
    public ResponseEntity<Object> handleQueryException(
            QueryException ex, WebRequest request) {
        log.error("Query error occurred", ex);
        return buildErrorResponse(ex, "Query error occurred", HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /**
     * Handles generic JavaAnalyserException instances
     */
    @ExceptionHandler(JavaAnalyserException.class)
    public ResponseEntity<Object> handleJavaAnalyserException(
            JavaAnalyserException ex, WebRequest request) {
        log.error("Application error occurred", ex);
        return buildErrorResponse(ex, "Application error occurred", HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /**
     * Fallback handler for all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGenericException(
            Exception ex, WebRequest request) {
        log.error("Unexpected error occurred", ex);
        return buildErrorResponse(ex, "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /**
     * Builds a standardized error response
     */
    private ResponseEntity<Object> buildErrorResponse(
            Exception ex, String message, HttpStatus status, WebRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("detail", ex.getMessage());
        body.put("path", request.getDescription(false).replace("uri=", ""));
        
        return new ResponseEntity<>(body, status);
    }
}