package personal_project.personal_project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import personal_project.personal_project.dto.SubtitleEntry;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw YouTube subtitle content (JSON3, XML, or WebVTT) into SubtitleEntry objects.
 */
@Slf4j
@Service
public class SubtitleParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Auto-detects format and delegates to the appropriate parser.
     *
     * @param rawContent raw subtitle string (JSON3, XML, or VTT)
     * @return list of SubtitleEntry with start/end (ms) and text
     */
    public List<SubtitleEntry> parse(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return List.of();
        }
        String trimmed = rawContent.strip();
        if (trimmed.startsWith("WEBVTT") || trimmed.contains("-->")) {
            log.debug("Detected VTT format, parsing as VTT");
            return parseVtt(trimmed);
        } else if (trimmed.startsWith("<") || trimmed.contains("<text")) {
            log.debug("Detected XML format, parsing as XML");
            return parseXml(trimmed);
        } else if (trimmed.contains("\"events\"")) {
            log.debug("Detected JSON3 format, parsing as JSON3");
            return parseJson3(trimmed);
        }
        log.warn("Unknown subtitle format, returning empty list");
        return List.of();
    }

    // -------------------------------------------------------------------------
    // JSON3 Parser  ({"events":[{"tStartMs":0,"dDurationMs":2000,"segs":[{"utf8":"Hello"}]}]})
    // -------------------------------------------------------------------------
    private List<SubtitleEntry> parseJson3(String json) {
        List<SubtitleEntry> entries = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode events = root.path("events");
            for (JsonNode event : events) {
                // Skip events with no segments or no text (e.g. pause events)
                JsonNode segs = event.path("segs");
                if (segs.isMissingNode() || !segs.isArray() || segs.isEmpty()) continue;

                long startMs = event.path("tStartMs").asLong(0);
                long durMs   = event.path("dDurationMs").asLong(2000);
                long endMs   = startMs + durMs;

                StringBuilder sb = new StringBuilder();
                for (JsonNode seg : segs) {
                    String utf8 = seg.path("utf8").asText("");
                    if (!utf8.isBlank()) sb.append(utf8);
                }

                String text = cleanText(sb.toString());
                if (!text.isBlank()) {
                    entries.add(new SubtitleEntry(startMs, endMs, text, null));
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse JSON3 subtitle: {}", e.getMessage(), e);
        }
        return entries;
    }

    // -------------------------------------------------------------------------
    // VTT Parser
    // -------------------------------------------------------------------------
    private List<SubtitleEntry> parseVtt(String vtt) {
        List<SubtitleEntry> entries = new ArrayList<>();
        // Split by blank lines
        String[] blocks = vtt.split("\\r?\\n\\r?\\n");
        Pattern timePattern = Pattern.compile(
                "(\\d{2}:\\d{2}:\\d{2}[.,]\\d{3})\\s*-->\\s*(\\d{2}:\\d{2}:\\d{2}[.,]\\d{3})"
        );
        for (String block : blocks) {
            block = block.strip();
            if (block.isEmpty() || block.startsWith("WEBVTT") || block.startsWith("NOTE")) continue;

            String[] lines = block.split("\\r?\\n");
            String timeLine = null;
            StringBuilder textBuilder = new StringBuilder();

            for (String line : lines) {
                if (timeLine == null && line.contains("-->")) {
                    timeLine = line;
                } else if (timeLine != null) {
                    if (!textBuilder.isEmpty()) textBuilder.append(" ");
                    textBuilder.append(line.strip());
                }
            }
            if (timeLine == null) continue;

            Matcher m = timePattern.matcher(timeLine);
            if (!m.find()) continue;

            long start = parseVttTimestamp(m.group(1));
            long end = parseVttTimestamp(m.group(2));
            String text = cleanText(textBuilder.toString());
            if (!text.isBlank()) {
                entries.add(new SubtitleEntry(start, end, text, null));
            }
        }
        return entries;
    }

    /** Parses VTT timestamp "HH:MM:SS.mmm" or "HH:MM:SS,mmm" → milliseconds */
    private long parseVttTimestamp(String ts) {
        ts = ts.replace(',', '.');
        String[] parts = ts.split(":");
        if (parts.length != 3) return 0;
        long hours = Long.parseLong(parts[0]);
        long minutes = Long.parseLong(parts[1]);
        String[] secMs = parts[2].split("\\.");
        long seconds = Long.parseLong(secMs[0]);
        long ms = secMs.length > 1 ? Long.parseLong(secMs[1]) : 0;
        return hours * 3600_000 + minutes * 60_000 + seconds * 1000 + ms;
    }

    // -------------------------------------------------------------------------
    // XML Parser  (<transcript><text start="1.2" dur="2.0">Hello</text></transcript>)
    // -------------------------------------------------------------------------
    private List<SubtitleEntry> parseXml(String xml) {
        List<SubtitleEntry> entries = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable external entity processing for security
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList textNodes = doc.getElementsByTagName("text");
            for (int i = 0; i < textNodes.getLength(); i++) {
                Element el = (Element) textNodes.item(i);
                String startStr = el.getAttribute("start");
                String durStr = el.getAttribute("dur");
                if (startStr.isEmpty()) continue;

                double startSec = Double.parseDouble(startStr);
                double durSec = durStr.isEmpty() ? 2.0 : Double.parseDouble(durStr);
                long startMs = (long) (startSec * 1000);
                long endMs = (long) ((startSec + durSec) * 1000);
                String text = cleanText(el.getTextContent());
                if (!text.isBlank()) {
                    entries.add(new SubtitleEntry(startMs, endMs, text, null));
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse XML subtitle: {}", e.getMessage(), e);
        }
        return entries;
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    /** Remove HTML tags, decode common HTML entities, trim whitespace */
    private String cleanText(String text) {
        if (text == null) return "";
        return text
                .replaceAll("<[^>]+>", "")           // strip HTML tags
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ")
                .replaceAll("\\s{2,}", " ")           // collapse multiple spaces
                .strip();
    }
}
