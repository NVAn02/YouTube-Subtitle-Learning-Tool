package personal_project.personal_project.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import personal_project.personal_project.repository.ErrorLogRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test: GlobalExceptionHandler must not swallow Spring's own MVC exceptions
 * (e.g. wrong HTTP method) into a generic 500 + spurious error log entry.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:global_exception_handler_test",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "admin.seed.password="
})
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ErrorLogRepository errorLogRepository;

    @BeforeEach
    void setUp() {
        errorLogRepository.deleteAll();
    }

    @Test
    void wrongHttpMethodReturns405NotAGenericServerError() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed());

        assertEquals(0, errorLogRepository.count(),
                "a routine 405 should not be recorded as an application error");
    }
}
