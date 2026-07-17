package personal_project.personal_project.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "translations",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"word", "sentence", "targetLang"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Translation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String word;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String sentence;

    @Column(nullable = false, length = 10)
    private String targetLang;

    @Column(nullable = false)
    private String translatedText;

    @Column(columnDefinition = "TEXT")
    private String explanation;
}
