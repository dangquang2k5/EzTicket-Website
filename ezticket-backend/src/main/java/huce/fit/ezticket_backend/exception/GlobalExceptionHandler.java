package huce.fit.ezticket_backend.exception;

import huce.fit.ezticket_backend.dto.common.ApiResponse;
import huce.fit.ezticket_backend.controller.TicketsController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Envelope error mapper (Context Pack 7.1).
 * Scope: ONLY endpoints created by SUBAGENT-29 (SCRUM-29, TicketsController).
 * AuthController and other controllers keep their current behaviour.
 */
@RestControllerAdvice(assignableTypes = TicketsController.class)
public class GlobalExceptionHandler {

    @ExceptionHandler(TicketException.class)
    public ResponseEntity<ApiResponse<?>> handleTicket(TicketException ex, HttpServletRequest request) {
        Map<String, String> errors = Map.of("reason", ex.getErrorCode());
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.fail(ex.getMessage(), ex.getErrorCode(), errors, request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("Bạn không có quyền truy cập Admin Portal.", "FORBIDDEN",
                        Map.of("reason", "FORBIDDEN"), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.put(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("Dữ liệu không hợp lệ", "VALIDATION_ERROR",
                        errors, request.getRequestURI()));
    }
}
