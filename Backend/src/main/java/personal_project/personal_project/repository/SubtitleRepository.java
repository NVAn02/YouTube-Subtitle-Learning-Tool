package personal_project.personal_project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import personal_project.personal_project.entity.Subtitle;

import java.util.List;

public interface SubtitleRepository extends JpaRepository<Subtitle, Long> {
    List<Subtitle> findByVideoIdOrderByStartMsAsc(Long videoId);
}
