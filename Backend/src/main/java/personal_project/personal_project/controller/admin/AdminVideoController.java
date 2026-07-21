package personal_project.personal_project.controller.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import personal_project.personal_project.dto.admin.AdminVideoResponse;
import personal_project.personal_project.repository.VideoRepository;

@RestController
@RequestMapping("/api/admin/videos")
@RequiredArgsConstructor
public class AdminVideoController {

    private final VideoRepository videoRepository;

    @GetMapping
    public Page<AdminVideoResponse> list(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return videoRepository.findAll(PageRequest.of(page, size))
                .map(video -> new AdminVideoResponse(video.getId(), video.getYoutubeId(), video.getSubtitles().size()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        videoRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
