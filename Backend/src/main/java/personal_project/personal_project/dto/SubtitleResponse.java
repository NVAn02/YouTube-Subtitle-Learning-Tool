package personal_project.personal_project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response body for POST /api/subtitles
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubtitleResponse {
    private String videoId;
    private List<SubtitleEntry> subtitles;
}
