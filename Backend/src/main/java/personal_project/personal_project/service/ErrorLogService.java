package personal_project.personal_project.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import personal_project.personal_project.entity.ErrorLog;
import personal_project.personal_project.entity.ErrorSeverity;
import personal_project.personal_project.repository.ErrorLogRepository;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorLogService {

    private final ErrorLogRepository errorLogRepository;

    public void record(ErrorSeverity severity, String source, String message, Throwable throwable, String context) {
        try {
            String stackTrace = null;
            if (throwable != null) {
                var sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                stackTrace = CredentialRedactor.redact(sw.toString());
            }

            var errorLog = ErrorLog.builder()
                    .severity(severity)
                    .source(source)
                    .message(CredentialRedactor.redact(message))
                    .stackTrace(stackTrace)
                    .context(context)
                    .build();
            errorLogRepository.save(errorLog);
        } catch (Exception e) {
            log.error("Failed to persist error log", e);
        }
    }

    public Page<ErrorLog> search(ErrorSeverity severity, String source, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return errorLogRepository.findAll(buildSpecification(severity, source, from, to), pageable);
    }

    public void delete(Long id) {
        errorLogRepository.deleteById(id);
    }

    public void deleteMatching(ErrorSeverity severity, String source, LocalDateTime from, LocalDateTime to) {
        var matches = errorLogRepository.findAll(buildSpecification(severity, source, from, to));
        errorLogRepository.deleteAll(matches);
    }

    private Specification<ErrorLog> buildSpecification(ErrorSeverity severity, String source, LocalDateTime from, LocalDateTime to) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (severity != null) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (source != null && !source.isBlank()) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
