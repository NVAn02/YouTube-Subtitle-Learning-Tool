package personal_project.personal_project.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fetches raw subtitle data from YouTube using yt-dlp as a subprocess.
 * yt-dlp is much more resilient to YouTube bot detection than scraping libraries,
 * and is regularly updated to stay ahead of YouTube's countermeasures.
 *
 * Subtitles are downloaded to a temp file in VTT format, converted to the XML format
 * expected by SubtitleParser, and the temp file is cleaned up afterward.
 *
 * Fallback: if yt-dlp is not installed, falls back to the original Java library.
 */
@Slf4j
@Service
public class SubtitleFetcher {

    @Value("${ytdlp.proxy-url:}")
    private String proxyUrl;

    /**
     * Fetch raw subtitle content for a given YouTube video ID.
     * Tries yt-dlp first; if unavailable, falls back to the Java library.
     *
     * @param videoId YouTube video ID (e.g. "arj7oStGLkU")
     * @return subtitle content as XML string, compatible with SubtitleParser
     * @throws SubtitleNotFoundException if no subtitles are found
     */
    public String fetchSubtitles(String videoId) {
        log.debug("Fetching subtitles for videoId={}", videoId);

        // Try yt-dlp first
        if (isYtDlpAvailable()) {
            try {
                return fetchWithYtDlp(videoId);
            } catch (SubtitleNotFoundException e) {
                throw e;
            } catch (Exception e) {
                log.warn("yt-dlp failed for videoId={}, error: {}", videoId, e.getMessage());
                throw new SubtitleNotFoundException("Could not retrieve subtitles for video " + videoId + ": " + e.getMessage());
            }
        }

        // Fallback to Java library
        log.warn("yt-dlp not found, falling back to Java library (may be blocked on cloud servers)");
        return fetchWithJavaLibrary(videoId);
    }

    // ─────────────────────────────────────────────
    // yt-dlp implementation
    // ─────────────────────────────────────────────

    private boolean isYtDlpAvailable() {
        try {
            Process p = new ProcessBuilder("yt-dlp", "--version")
                    .redirectErrorStream(true)
                    .start();
            int exit = p.waitFor();
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static final String COOKIES_PATH = "/app/cookies.txt";

    private String fetchWithYtDlp(String videoId) throws Exception {
        String tmpDir = System.getProperty("java.io.tmpdir");
        String outTemplate = tmpDir + "/yt-subtitle-" + UUID.randomUUID() + ".%(ext)s";

        // Build yt-dlp command:
        // --write-auto-sub      → auto-generated subtitles
        // --sub-lang            → prefer English
        // --skip-download       → don't download video
        // --convert-subs vtt    → convert to VTT format
        // --js-runtimes node    → use Node.js as JS runtime
        // --remote-components   → download EJS challenge solver (bypasses n-challenge bot detection)
        List<String> cmd = new ArrayList<>(List.of(
                "yt-dlp",
                "--write-auto-sub",
                "--write-sub",
                "--sub-lang", "en.*,vi",
                "--skip-download",
                "--convert-subs", "vtt",
                "-o", outTemplate,
                "--no-playlist",
                "--quiet",
                "--js-runtimes", "node",
                "--remote-components", "ejs:github"
        ));

        // Add proxy if configured (changes outbound IP)
        if (proxyUrl != null && !proxyUrl.isBlank()) {
            cmd.add("--proxy");
            cmd.add(proxyUrl);
            log.debug("Using proxy for yt-dlp");
        }

        // Add cookies if available (provides YouTube authentication)
        File cookiesFile = new File(COOKIES_PATH);
        if (cookiesFile.exists() && cookiesFile.isFile()) {
            cmd.add("--cookies");
            cmd.add(COOKIES_PATH);
            log.debug("Using cookies from {}", COOKIES_PATH);
        } else {
            log.warn("No cookies file found at {}. YouTube may block requests.", COOKIES_PATH);
        }

        cmd.add("https://www.youtube.com/watch?v=" + videoId);

        log.debug("Running yt-dlp for videoId={}", videoId);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Capture output for debugging
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        log.debug("yt-dlp exit={}, output={}", exitCode, output);

        // Find the downloaded .vtt file
        File tmpDirFile = new File(tmpDir);
        File[] vttFiles = tmpDirFile.listFiles((dir, name) ->
                name.startsWith("yt-subtitle-") && name.endsWith(".vtt"));

        if (vttFiles == null || vttFiles.length == 0) {
            throw new SubtitleNotFoundException("yt-dlp found no subtitles for video: " + videoId);
        }

        // Pick English subtitle if available, else first
        File vttFile = null;
        for (File f : vttFiles) {
            if (f.getName().contains(".en") || f.getName().contains("en.")) {
                vttFile = f;
                break;
            }
        }
        if (vttFile == null) vttFile = vttFiles[0];

        log.debug("Parsing VTT file: {}", vttFile.getName());
        String vttContent = Files.readString(vttFile.toPath());

        // Cleanup all temp files
        for (File f : vttFiles) f.delete();

        return vttToXml(vttContent);
    }

    /**
     * Converts VTT subtitle content to the XML format SubtitleParser expects.
     * VTT format: "HH:MM:SS.mmm --> HH:MM:SS.mmm\nText"
     * XML format: <transcript><text start="1.23" dur="2.00">Text</text>...</transcript>
     */
    private String vttToXml(String vtt) {
        StringBuilder xml = new StringBuilder("<transcript>\n");
        String[] lines = vtt.split("\\r?\\n");

        double currentStart = -1;
        double currentEnd = -1;
        StringBuilder currentText = new StringBuilder();

        for (String line : lines) {
            line = line.trim();

            // Parse timecode line: "00:00:01.234 --> 00:00:03.456"
            if (line.contains("-->")) {
                // Save previous entry
                if (currentStart >= 0 && currentText.length() > 0) {
                    double dur = Math.max(0.1, currentEnd - currentStart);
                    String text = escapeXml(currentText.toString().trim());
                    if (!text.isEmpty()) {
                        xml.append(String.format("  <text start=\"%.3f\" dur=\"%.3f\">%s</text>%n",
                                currentStart, dur, text));
                    }
                }

                String[] parts = line.split("-->");
                currentStart = parseVttTime(parts[0].trim());
                // Remove VTT positioning tags after timestamp if present
                String endPart = parts[1].trim().split("\\s+")[0];
                currentEnd = parseVttTime(endPart);
                currentText = new StringBuilder();
            } else if (!line.isEmpty() && !line.startsWith("WEBVTT") &&
                       !line.startsWith("NOTE") && !line.matches("\\d+")) {
                // Text line — strip VTT formatting tags like <c>, <00:00:00.000>
                String cleaned = line.replaceAll("<[^>]+>", "").trim();
                if (!cleaned.isEmpty()) {
                    if (currentText.length() > 0) currentText.append(" ");
                    currentText.append(cleaned);
                }
            }
        }

        // Flush last entry
        if (currentStart >= 0 && currentText.length() > 0) {
            double dur = Math.max(0.1, currentEnd - currentStart);
            String text = escapeXml(currentText.toString().trim());
            if (!text.isEmpty()) {
                xml.append(String.format("  <text start=\"%.3f\" dur=\"%.3f\">%s</text>%n",
                        currentStart, dur, text));
            }
        }

        xml.append("</transcript>");
        return xml.toString();
    }

    /** Parse VTT timestamp "HH:MM:SS.mmm" or "MM:SS.mmm" into seconds */
    private double parseVttTime(String ts) {
        try {
            String[] parts = ts.split(":");
            if (parts.length == 3) {
                double h = Double.parseDouble(parts[0]);
                double m = Double.parseDouble(parts[1]);
                double s = Double.parseDouble(parts[2]);
                return h * 3600 + m * 60 + s;
            } else if (parts.length == 2) {
                double m = Double.parseDouble(parts[0]);
                double s = Double.parseDouble(parts[1]);
                return m * 60 + s;
            }
        } catch (NumberFormatException ignored) {}
        return 0;
    }

    private String escapeXml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    // ─────────────────────────────────────────────
    // Java library fallback
    // ─────────────────────────────────────────────

    private String fetchWithJavaLibrary(String videoId) {
        try {
            io.github.thoroldvix.api.YoutubeTranscriptApi api =
                    io.github.thoroldvix.api.TranscriptApiFactory.createDefault();
            io.github.thoroldvix.api.TranscriptList transcriptList = api.listTranscripts(videoId);

            io.github.thoroldvix.api.Transcript selected = null;
            try { selected = transcriptList.findManualTranscript("en"); } catch (Exception ignored) {}
            if (selected == null) {
                try { selected = transcriptList.findGeneratedTranscript("en"); } catch (Exception ignored) {}
            }
            if (selected == null) {
                for (io.github.thoroldvix.api.Transcript t : transcriptList) { selected = t; break; }
            }
            if (selected == null) {
                throw new SubtitleNotFoundException("No subtitle tracks found for video: " + videoId);
            }

            io.github.thoroldvix.api.TranscriptContent content = selected.fetch();
            List<io.github.thoroldvix.api.TranscriptContent.Fragment> fragments = content.getContent();
            if (fragments == null || fragments.isEmpty()) {
                throw new SubtitleNotFoundException("Empty transcript content for video: " + videoId);
            }

            StringBuilder sb = new StringBuilder("<transcript>\n");
            for (io.github.thoroldvix.api.TranscriptContent.Fragment f : fragments) {
                String text = escapeXml(f.getText());
                sb.append(String.format("  <text start=\"%.3f\" dur=\"%.3f\">%s</text>%n",
                        f.getStart(), f.getDur(), text));
            }
            sb.append("</transcript>");
            return sb.toString();

        } catch (SubtitleNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Java library failed for videoId={}: {}", videoId, e.getMessage());
            throw new SubtitleNotFoundException("Could not retrieve subtitles for video " + videoId + ": " + e.getMessage());
        }
    }

    // ---- Custom exception ----
    public static class SubtitleNotFoundException extends RuntimeException {
        public SubtitleNotFoundException(String message) { super(message); }
    }
}
