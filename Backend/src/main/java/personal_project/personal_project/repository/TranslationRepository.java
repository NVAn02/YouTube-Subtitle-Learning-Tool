package personal_project.personal_project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import personal_project.personal_project.entity.Translation;

import java.util.Optional;

public interface TranslationRepository extends JpaRepository<Translation, Long> {
    Optional<Translation> findByWordAndSentenceAndTargetLang(String word, String sentence, String targetLang);
}
