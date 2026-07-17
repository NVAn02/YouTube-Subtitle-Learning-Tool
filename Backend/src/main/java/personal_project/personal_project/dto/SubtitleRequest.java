package personal_project.personal_project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/subtitles
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubtitleRequest {
    private String youtubeUrl;
}
