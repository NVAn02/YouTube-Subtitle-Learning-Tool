package personal_project.personal_project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import personal_project.personal_project.entity.Video;

import java.util.Optional;

public interface VideoRepository extends JpaRepository<Video, Long> {
    Optional<Video> findByYoutubeId(String youtubeId);
}
