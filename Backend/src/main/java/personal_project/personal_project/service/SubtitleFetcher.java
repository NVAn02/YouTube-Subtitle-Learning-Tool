package personal_project.personal_project.service;

import io.github.thoroldvix.api.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fetches raw subtitle data from YouTube using the io.github.thoroldvix:youtube-transcript-api
 * Java library, which correctly handles YouTube's internal scraping, player response parsing,
 * and timedtext API calls.
 *
 * The result is returned as a simple XML string compatible with SubtitleParser.
 */
@Slf4j
@Service
public class SubtitleFetcher {

    private final YoutubeTranscriptApi youtubeTranscriptApi;

    public SubtitleFetcher() {
        this.youtubeTranscriptApi = TranscriptApiFactory.createDefault();
    }

    /**
     * Fetch raw subtitle content for a given YouTube video ID.
     * Prefers manual English, falls back to auto-generated English, then Vietnamese, then first available.
     *
     * @param videoId YouTube video ID (e.g. "arj7oStGLkU")
     * @return subtitle content as XML string, compatible with SubtitleParser
     * @throws SubtitleNotFoundException if no subtitles are found
     */
    public String fetchSubtitles(String videoId) {
        log.debug("Fetching subtitles for videoId={}", videoId);
        try {
            TranscriptList transcriptList = youtubeTranscriptApi.listTranscripts(videoId);

            // Log all available tracks
            for (Transcript t : transcriptList) {
                log.debug("Track: lang={} ({}), generated={}", t.getLanguageCode(), t.getLanguage(), t.isGenerated());
            }

            // Priority: manual en → generated en → vi → first available
            Transcript selected = null;
            try { selected = transcriptList.findManualTranscript("en"); }
            catch (Exception ignored) {}

            if (selected == null) {
                try { selected = transcriptList.findGeneratedTranscript("en"); }
                catch (Exception ignored) {}
            }

            if (selected == null) {
                try { selected = transcriptList.findTranscript("vi", "en"); }
                catch (Exception ignored) {}
            }

            if (selected == null) {
                // Take the first available
                for (Transcript t : transcriptList) { selected = t; break; }
            }

            if (selected == null) {
                throw new SubtitleNotFoundException("No subtitle tracks found for video: " + videoId);
            }

            log.debug("Selected transcript: lang={}, generated={}", selected.getLanguageCode(), selected.isGenerated());

            TranscriptContent content = selected.fetch();
            List<TranscriptContent.Fragment> fragments = content.getContent();

            if (fragments == null || fragments.isEmpty()) {
                throw new SubtitleNotFoundException("Empty transcript content for video: " + videoId);
            }

            // Convert to XML format that SubtitleParser expects
            String xml = toXml(fragments);
            log.debug("Fetched {} subtitle fragments for videoId={}", fragments.size(), videoId);
            return xml;

        } catch (SubtitleNotFoundException e) {
            throw e;
        } catch (TranscriptRetrievalException e) {
            log.warn("Transcript retrieval failed for videoId={}: {}", videoId, e.getMessage());
            throw new SubtitleNotFoundException("Could not retrieve subtitles for video " + videoId + ": " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error fetching subtitles for videoId={}: {}", videoId, e.getMessage(), e);
            throw new SubtitleNotFoundException("Error fetching subtitles: " + e.getMessage());
        }
    }

    /**
     * Converts the transcript fragment list to XML in the format SubtitleParser expects:
     * {@code <transcript><text start="1.23" dur="2.00">Text content</text>...</transcript>}
     */
    private String toXml(List<TranscriptContent.Fragment> fragments) {
        StringBuilder sb = new StringBuilder("<transcript>\n");
        for (TranscriptContent.Fragment f : fragments) {
            double start = f.getStart();
            double dur = f.getDur();
            String text = f.getText()
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
            sb.append(String.format("  <text start=\"%.3f\" dur=\"%.3f\">%s</text>%n", start, dur, text));
        }
        sb.append("</transcript>");
        return sb.toString();
    }

    // ---- Custom exception ----
    public static class SubtitleNotFoundException extends RuntimeException {
        public SubtitleNotFoundException(String message) { super(message); }
    }
}
