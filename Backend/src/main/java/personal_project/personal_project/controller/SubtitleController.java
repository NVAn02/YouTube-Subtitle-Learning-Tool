package personal_project.personal_project.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import personal_project.personal_project.dto.*;
import personal_project.personal_project.entity.Video;
import personal_project.personal_project.repository.SubtitleRepository;
import personal_project.personal_project.repository.VideoRepository;
import personal_project.personal_project.entity.ErrorSeverity;
import personal_project.personal_project.service.ErrorLogService;
import personal_project.personal_project.service.NlpService;
import personal_project.personal_project.service.SubtitleFetcher;
import personal_project.personal_project.service.SubtitleParser;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * REST controller for subtitle fetching and tokenization.
 * POST /api/subtitles
 */
@Slf4j
@RestController
@RequestMapping("/api/subtitles")
@RequiredArgsConstructor
public class SubtitleController {

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile(
            "(?:youtube\\.com/(?:watch\\?v=|shorts/|embed/)|youtu\\.be/)([a-zA-Z0-9_-]{11})"
    );

    private final SubtitleFetcher subtitleFetcher;
    private final SubtitleParser subtitleParser;
    private final NlpService nlpService;
    private final VideoRepository videoRepository;
    private final SubtitleRepository subtitleRepository;
    private final ObjectMapper objectMapper;
    private final ErrorLogService errorLogService;

    @PostMapping
    public ResponseEntity<?> getSubtitles(@RequestBody SubtitleRequest request) {
        String youtubeUrl = request.getYoutubeUrl();
        if (youtubeUrl == null || youtubeUrl.isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("youtubeUrl is required"));
        }

        String videoId = extractVideoId(youtubeUrl);
        if (videoId == null) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Invalid YouTube URL"));
        }

        log.info("Processing subtitles for videoId={}", videoId);

        try {
            // 1. Check if we already have it in DB
            Optional<Video> existingVideo = videoRepository.findByYoutubeId(videoId);
            if (existingVideo.isPresent()) {
                log.info("Subtitles found in DB for videoId={}", videoId);
                List<personal_project.personal_project.entity.Subtitle> dbSubtitles = subtitleRepository.findByVideoIdOrderByStartMsAsc(existingVideo.get().getId());
                
                List<SubtitleEntry> tokenizedEntries = dbSubtitles.stream().map(dbSub -> {
                    try {
                        List<String> tokens = objectMapper.readValue(dbSub.getTokensJson(), new TypeReference<List<String>>() {});
                        return new SubtitleEntry(dbSub.getStartMs(), dbSub.getEndMs(), dbSub.getText(), tokens);
                    } catch (Exception e) {
                        return new SubtitleEntry(dbSub.getStartMs(), dbSub.getEndMs(), dbSub.getText(), List.of());
                    }
                }).collect(Collectors.toList());
                
                return ResponseEntity.ok(new SubtitleResponse(videoId, tokenizedEntries));
            }

            // 2. Not in DB -> fetch from YouTube
            log.info("Fetching subtitles from YouTube for videoId={}", videoId);
            String rawContent = subtitleFetcher.fetchSubtitles(videoId);
            List<SubtitleEntry> entries = subtitleParser.parse(rawContent);

            if (entries.isEmpty()) {
                return ResponseEntity.status(404)
                        .body(new ErrorResponse("No subtitles found for this video"));
            }

            // Tokenize each entry
            List<SubtitleEntry> tokenizedEntries = entries.stream()
                    .map(entry -> new SubtitleEntry(
                            entry.getStart(),
                            entry.getEnd(),
                            entry.getText(),
                            nlpService.tokenize(entry.getText())
                    ))
                    .collect(Collectors.toList());

            // 3. Save to DB
            Video video = Video.builder().youtubeId(videoId).build();
            video = videoRepository.save(video);
            
            Video finalVideo = video;
            List<personal_project.personal_project.entity.Subtitle> entities = tokenizedEntries.stream().map(entry -> {
                try {
                    String tokensJson = objectMapper.writeValueAsString(entry.getTokens());
                    return personal_project.personal_project.entity.Subtitle.builder()
                            .video(finalVideo)
                            .startMs(entry.getStart())
                            .endMs(entry.getEnd())
                            .text(entry.getText())
                            .tokensJson(tokensJson)
                            .build();
                } catch (Exception e) {
                    return null;
                }
            }).filter(e -> e != null).collect(Collectors.toList());
            
            subtitleRepository.saveAll(entities);

            return ResponseEntity.ok(new SubtitleResponse(videoId, tokenizedEntries));

        } catch (SubtitleFetcher.SubtitleNotFoundException e) {
            log.warn("No subtitles for videoId={}: {}", videoId, e.getMessage());
            errorLogService.record(ErrorSeverity.WARN, "SUBTITLE_FETCH", e.getMessage(), e, videoId);
            return ResponseEntity.status(404).body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error processing videoId={}: {}", videoId, e.getMessage(), e);
            errorLogService.record(ErrorSeverity.ERROR, "SUBTITLE_FETCH", e.getMessage(), e, videoId);
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("An unexpected error occurred. Please try again."));
        }
    }

    private String extractVideoId(String url) {
        Matcher matcher = VIDEO_ID_PATTERN.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }
}
