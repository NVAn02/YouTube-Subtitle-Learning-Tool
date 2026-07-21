package personal_project.personal_project.controller.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import personal_project.personal_project.dto.admin.AdminTranslationResponse;
import personal_project.personal_project.repository.TranslationRepository;

@RestController
@RequestMapping("/api/admin/translations")
@RequiredArgsConstructor
public class AdminTranslationController {

    private final TranslationRepository translationRepository;

    @GetMapping
    public Page<AdminTranslationResponse> list(@RequestParam(defaultValue = "") String word,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        Page<personal_project.personal_project.entity.Translation> translations = word.isBlank()
                ? translationRepository.findAll(PageRequest.of(page, size))
                : translationRepository.findByWordContainingIgnoreCase(word, PageRequest.of(page, size));
        return translations.map(t -> new AdminTranslationResponse(
                t.getId(), t.getWord(), t.getSentence(), t.getTargetLang(), t.getTranslatedText()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        translationRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
