package personal_project.personal_project.controller.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import personal_project.personal_project.dto.admin.ErrorLogResponse;
import personal_project.personal_project.entity.ErrorLog;
import personal_project.personal_project.entity.ErrorSeverity;
import personal_project.personal_project.service.ErrorLogService;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/error-logs")
@RequiredArgsConstructor
public class AdminErrorLogController {

    private final ErrorLogService errorLogService;

    @GetMapping
    public Page<ErrorLogResponse> list(@RequestParam(required = false) ErrorSeverity severity,
                                        @RequestParam(required = false) String source,
                                        @RequestParam(required = false) LocalDateTime from,
                                        @RequestParam(required = false) LocalDateTime to,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return errorLogService.search(severity, source, from, to, PageRequest.of(page, size))
                .map(this::toResponse);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        errorLogService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteMatching(@RequestParam(required = false) ErrorSeverity severity,
                                                @RequestParam(required = false) String source,
                                                @RequestParam(required = false) LocalDateTime from,
                                                @RequestParam(required = false) LocalDateTime to) {
        errorLogService.deleteMatching(severity, source, from, to);
        return ResponseEntity.noContent().build();
    }

    private ErrorLogResponse toResponse(ErrorLog log) {
        return new ErrorLogResponse(log.getId(), log.getOccurredAt(), log.getSeverity().name(),
                log.getSource(), log.getMessage(), log.getStackTrace(), log.getContext());
    }
}
