package personal_project.personal_project.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminVideoResponse {
    private Long id;
    private String youtubeId;
    private int subtitleCount;
}
