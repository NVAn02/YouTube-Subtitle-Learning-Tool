package personal_project.personal_project.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "saved_words")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedWord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String word;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String sentence;

    @Column(nullable = false)
    private String translation;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(nullable = false, length = 10)
    private String targetLang;

    @Column(nullable = false)
    private LocalDateTime savedAt;

    @PrePersist
    protected void onCreate() {
        savedAt = LocalDateTime.now();
    }
}
