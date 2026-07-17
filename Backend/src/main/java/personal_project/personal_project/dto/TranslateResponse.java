package personal_project.personal_project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body for POST /api/translate
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranslateResponse {
    private String translation;
    private String explanation;
}
