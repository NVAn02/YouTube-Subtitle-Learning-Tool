package personal_project.personal_project.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Wraps Apache OpenNLP TokenizerME.
 * The model is loaded once at startup (singleton Spring bean).
 */
@Slf4j
@Service
public class NlpService {

    private static final String MODEL_PATH = "models/en-token.bin";

    private TokenizerME tokenizer;

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource(MODEL_PATH);
            try (InputStream is = resource.getInputStream()) {
                TokenizerModel model = new TokenizerModel(is);
                this.tokenizer = new TokenizerME(model);
                log.info("OpenNLP tokenizer model loaded from {}", MODEL_PATH);
            }
        } catch (IOException e) {
            log.error("Failed to load OpenNLP model from {}: {}", MODEL_PATH, e.getMessage());
            log.warn("NlpService will use simple whitespace tokenizer as fallback");
            this.tokenizer = null;
        }
    }

    /**
     * Tokenizes a sentence into a list of tokens.
     * Falls back to simple whitespace splitting if model is unavailable.
     *
     * @param sentence input text
     * @return list of tokens
     */
    public List<String> tokenize(String sentence) {
        if (sentence == null || sentence.isBlank()) {
            return List.of();
        }
        if (tokenizer != null) {
            synchronized (this) {
                // TokenizerME is NOT thread-safe — synchronize access
                String[] tokens = tokenizer.tokenize(sentence);
                return Arrays.asList(tokens);
            }
        }
        // Fallback: split on whitespace
        return Arrays.asList(sentence.trim().split("\\s+"));
    }

    /**
     * Returns true if the token is a pure punctuation mark (should not be clickable).
     */
    public static boolean isPunctuation(String token) {
        return token != null && token.matches("[\\p{Punct}]+");
    }
}
