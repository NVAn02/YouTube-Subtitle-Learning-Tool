package personal_project.personal_project.service;

import personal_project.personal_project.entity.Translation;
import personal_project.personal_project.repository.TranslationRepository;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Translates a word in context using Google Gemini via LangChain4j.
 * Results are cached in MySQL database to avoid redundant API calls.
 */
@Slf4j
@Service
public class TranslationService {

    private final ChatLanguageModel chatModel;
    private final TranslationRepository translationRepository;
    private final ObjectMapper objectMapper;

    public TranslationService(
            @Value("${gemini.api-key}") String apiKey,
            @Value("${gemini.model-name:gemini-flash-latest}") String modelName,
            TranslationRepository translationRepository,
            ObjectMapper objectMapper
    ) {
        this.translationRepository = translationRepository;
        this.objectMapper = objectMapper;
        this.chatModel = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.2)
                .build();
        log.info("TranslationService initialized with model={}", modelName);
    }

    /**
     * Translate a word given its surrounding sentence context.
     *
     * @param word       the word to translate
     * @param sentence   the full sentence for context
     * @param targetLang target language code (e.g. "vi" for Vietnamese)
     * @return translated string
     */
    public Translation translate(String word, String sentence, String targetLang) {
        Optional<Translation> existing = translationRepository.findByWordAndSentenceAndTargetLang(word, sentence, targetLang);
        if (existing.isPresent()) {
            log.debug("DB Cache hit for word='{}' lang={}", word, targetLang);
            return existing.get();
        }

        log.debug("Calling Gemini to translate word='{}' lang={}", word, targetLang);
        String prompt = buildPrompt(word, sentence, targetLang);

        try {
            String result = chatModel.generate(prompt);
            String jsonStr = result.replaceAll("```json", "").replaceAll("```", "").trim();
            JsonNode jsonNode = objectMapper.readTree(jsonStr);
            String translationText = jsonNode.has("translation") ? jsonNode.get("translation").asText() : "";
            String explanation = jsonNode.has("explanation") ? jsonNode.get("explanation").asText() : "";
            
            // Save to DB
            Translation translation = Translation.builder()
                    .word(word)
                    .sentence(sentence)
                    .targetLang(targetLang)
                    .translatedText(translationText)
                    .explanation(explanation)
                    .build();
            translationRepository.save(translation);
            
            return translation;
        } catch (Exception e) {
            log.error("Gemini translation failed: {}", e.getMessage());
            throw new TranslationException("Translation service unavailable. Please try again.", e);
        }
    }

    private String buildPrompt(String word, String sentence, String targetLang) {
        return String.format(
                "You are a dictionary assistant. Translate the word \"%s\" as it appears in this sentence: \"%s\". " +
                "Target language: %s. " +
                "Reply ONLY with a valid JSON object with two string properties: " +
                "\"translation\" (the translated word or short phrase) and " +
                "\"explanation\" (a brief explanation and an example sentence. This entire explanation MUST be written in the %s language). " +
                "Do not include markdown blocks or any other text.",
                word, sentence, targetLang, targetLang
        );
    }


    // ---- Custom exception ----
    public static class TranslationException extends RuntimeException {
        public TranslationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
