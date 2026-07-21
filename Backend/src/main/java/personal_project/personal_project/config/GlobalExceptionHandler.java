package personal_project.personal_project.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import personal_project.personal_project.dto.ErrorResponse;
import personal_project.personal_project.entity.ErrorSeverity;
import personal_project.personal_project.service.ErrorLogService;

/**
 * Extends ResponseEntityExceptionHandler so Spring's own MVC exceptions (405, malformed JSON, etc.)
 * keep their normal status handling - only exceptions with no more specific handler reach handleUncaught.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final ErrorLogService errorLogService;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUncaught(Exception e) {
        log.error("Unhandled exception: {}", e.getMessage(), e);
        errorLogService.record(ErrorSeverity.ERROR, "UNCAUGHT", e.getMessage(), e, null);
        return ResponseEntity.internalServerError().body(new ErrorResponse("An unexpected error occurred."));
    }
}
