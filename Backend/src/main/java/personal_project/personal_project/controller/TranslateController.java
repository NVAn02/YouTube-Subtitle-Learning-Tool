package personal_project.personal_project.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import personal_project.personal_project.dto.ErrorResponse;
import personal_project.personal_project.dto.TranslateRequest;
import personal_project.personal_project.dto.TranslateResponse;
import personal_project.personal_project.entity.ErrorSeverity;
import personal_project.personal_project.service.ErrorLogService;
import personal_project.personal_project.service.NlpService;
import personal_project.personal_project.service.TranslationService;

/**
 * REST controller for word-in-context translation.
 * POST /api/translate
 */
@Slf4j
@RestController
@RequestMapping("/api/translate")
@RequiredArgsConstructor
public class TranslateController {

    private final TranslationService translationService;
    private final ErrorLogService errorLogService;

    @PostMapping
    public ResponseEntity<?> translate(@RequestBody TranslateRequest request) {
        if (request.getWord() == null || request.getWord().isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("word is required"));
        }
        if (request.getSentence() == null || request.getSentence().isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("sentence is required"));
        }

        // Reject pure punctuation
        if (NlpService.isPunctuation(request.getWord().trim())) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Punctuation marks cannot be translated"));
        }

        String targetLang = (request.getTargetLang() == null || request.getTargetLang().isBlank())
                ? "vi" : request.getTargetLang();

        log.info("Translate word='{}' lang={}", request.getWord(), targetLang);

        try {
            personal_project.personal_project.entity.Translation translation = translationService.translate(
                    request.getWord().trim(),
                    request.getSentence().trim(),
                    targetLang
            );
            return ResponseEntity.ok(new TranslateResponse(translation.getTranslatedText(), translation.getExplanation()));
        } catch (TranslationService.TranslationException e) {
            log.error("Translation failed: {}", e.getMessage());
            errorLogService.record(ErrorSeverity.ERROR, "TRANSLATION", e.getMessage(), e, request.getWord());
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("Không dịch được, thử lại sau."));
        }
    }
}
