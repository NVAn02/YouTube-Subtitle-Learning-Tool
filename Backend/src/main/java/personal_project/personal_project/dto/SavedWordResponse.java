package personal_project.personal_project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SavedWordResponse {
    private Long id;
    private String word;
    private String sentence;
    private String translation;
    private String explanation;
    private String targetLang;
    private LocalDateTime savedAt;
}
