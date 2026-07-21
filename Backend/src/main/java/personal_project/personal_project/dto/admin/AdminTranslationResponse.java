package personal_project.personal_project.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminTranslationResponse {
    private Long id;
    private String word;
    private String sentence;
    private String targetLang;
    private String translatedText;
}
