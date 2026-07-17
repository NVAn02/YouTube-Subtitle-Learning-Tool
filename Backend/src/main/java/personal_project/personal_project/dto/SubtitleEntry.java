package personal_project.personal_project.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a single subtitle entry with tokenized words.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubtitleEntry {
    /** Start time in milliseconds */
    private long start;
    /** End time in milliseconds */
    private long end;
    /** Raw text of the subtitle */
    private String text;
    /** List of tokens (words/punctuation) split by OpenNLP */
    private List<String> tokens;
}
