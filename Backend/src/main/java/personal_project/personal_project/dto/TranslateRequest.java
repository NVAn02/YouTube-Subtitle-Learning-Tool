package personal_project.personal_project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/translate
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranslateRequest {
    private String word;
    private String sentence;
    private String targetLang;
}
