package personal_project.personal_project.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorLogResponse {
    private Long id;
    private LocalDateTime occurredAt;
    private String severity;
    private String source;
    private String message;
    private String stackTrace;
    private String context;
}
