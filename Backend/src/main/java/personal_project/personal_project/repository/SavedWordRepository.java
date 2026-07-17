package personal_project.personal_project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import personal_project.personal_project.entity.SavedWord;

import java.util.List;
import java.util.Optional;

public interface SavedWordRepository extends JpaRepository<SavedWord, Long> {
    List<SavedWord> findByUserIdOrderBySavedAtDesc(Long userId);
    Optional<SavedWord> findByUserIdAndWordAndTargetLang(Long userId, String word, String targetLang);
}
