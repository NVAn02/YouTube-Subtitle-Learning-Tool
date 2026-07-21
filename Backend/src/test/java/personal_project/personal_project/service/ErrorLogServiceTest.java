package personal_project.personal_project.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import personal_project.personal_project.entity.ErrorLog;
import personal_project.personal_project.entity.ErrorSeverity;
import personal_project.personal_project.repository.ErrorLogRepository;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:error_log_service_test",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "admin.seed.password="
})
class ErrorLogServiceTest {

    @Autowired
    private ErrorLogService errorLogService;
    @Autowired
    private ErrorLogRepository errorLogRepository;

    @BeforeEach
    void setUp() {
        errorLogRepository.deleteAll();
    }

    @Test
    void recordPersistsRedactedErrorLog() {
        String messageWithCredentials = "yt-dlp failed calling http://scraperapi:SECRET123@proxy-server.scraperapi.com:8001";

        errorLogService.record(ErrorSeverity.WARN, "SUBTITLE_FETCH", messageWithCredentials, null, "abc123");

        var all = errorLogRepository.findAll();
        assertEquals(1, all.size());
        ErrorLog saved = all.get(0);
        assertEquals(ErrorSeverity.WARN, saved.getSeverity());
        assertEquals("SUBTITLE_FETCH", saved.getSource());
        assertEquals("abc123", saved.getContext());
        assertFalse(saved.getMessage().contains("SECRET123"), "credentials should be redacted from the persisted message");
        assertNotNull(saved.getOccurredAt());
    }

    @Test
    void searchFiltersBySeverityAndSource() {
        errorLogService.record(ErrorSeverity.WARN, "SUBTITLE_FETCH", "no subs", null, "vid1");
        errorLogService.record(ErrorSeverity.ERROR, "TRANSLATION", "gemini failed", null, "word1");

        Page<ErrorLog> warnOnly = errorLogService.search(ErrorSeverity.WARN, null, null, null, PageRequest.of(0, 10));
        assertEquals(1, warnOnly.getTotalElements());
        assertEquals("SUBTITLE_FETCH", warnOnly.getContent().get(0).getSource());

        Page<ErrorLog> translationOnly = errorLogService.search(null, "TRANSLATION", null, null, PageRequest.of(0, 10));
        assertEquals(1, translationOnly.getTotalElements());
    }

    @Test
    void deleteRemovesTheLog() {
        errorLogService.record(ErrorSeverity.ERROR, "UNCAUGHT", "boom", null, null);
        Long id = errorLogRepository.findAll().get(0).getId();

        errorLogService.delete(id);

        assertTrue(errorLogRepository.findById(id).isEmpty());
    }

    @Test
    void recordNeverThrowsEvenIfCalledRepeatedly() {
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 3; i++) {
                errorLogService.record(ErrorSeverity.ERROR, "UNCAUGHT", "err " + i, new RuntimeException("boom"), null);
            }
        });
        assertEquals(3, errorLogRepository.count());
    }
}
