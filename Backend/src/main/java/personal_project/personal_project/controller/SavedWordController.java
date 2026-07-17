package personal_project.personal_project.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import personal_project.personal_project.dto.ErrorResponse;
import personal_project.personal_project.dto.SavedWordRequest;
import personal_project.personal_project.dto.SavedWordResponse;
import personal_project.personal_project.entity.SavedWord;
import personal_project.personal_project.entity.User;
import personal_project.personal_project.repository.SavedWordRepository;
import personal_project.personal_project.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/saved-words")
@RequiredArgsConstructor
public class SavedWordController {

    private final SavedWordRepository savedWordRepository;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<?> saveWord(@RequestBody SavedWordRequest request, Authentication authentication) {
        String username = authentication.getName();
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(new ErrorResponse("User not found"));
        }
        User user = userOpt.get();

        // Check if already saved
        Optional<SavedWord> existing = savedWordRepository.findByUserIdAndWordAndTargetLang(user.getId(), request.getWord(), request.getTargetLang());
        if (existing.isPresent()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Word already saved"));
        }

        SavedWord savedWord = SavedWord.builder()
                .user(user)
                .word(request.getWord())
                .sentence(request.getSentence())
                .translation(request.getTranslation())
                .explanation(request.getExplanation())
                .targetLang(request.getTargetLang())
                .build();
        
        savedWord = savedWordRepository.save(savedWord);
        
        return ResponseEntity.ok(mapToResponse(savedWord));
    }

    @GetMapping
    public ResponseEntity<?> getSavedWords(Authentication authentication) {
        String username = authentication.getName();
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(new ErrorResponse("User not found"));
        }
        User user = userOpt.get();

        List<SavedWord> words = savedWordRepository.findByUserIdOrderBySavedAtDesc(user.getId());
        List<SavedWordResponse> response = words.stream().map(this::mapToResponse).collect(Collectors.toList());
        
        return ResponseEntity.ok(response);
    }

    private SavedWordResponse mapToResponse(SavedWord savedWord) {
        return SavedWordResponse.builder()
                .id(savedWord.getId())
                .word(savedWord.getWord())
                .sentence(savedWord.getSentence())
                .translation(savedWord.getTranslation())
                .explanation(savedWord.getExplanation())
                .targetLang(savedWord.getTargetLang())
                .savedAt(savedWord.getSavedAt())
                .build();
    }
}
