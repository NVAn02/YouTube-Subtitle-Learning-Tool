package personal_project.personal_project.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import personal_project.personal_project.entity.Role;
import personal_project.personal_project.entity.User;
import personal_project.personal_project.repository.UserRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.seed.username}")
    private String seedUsername;

    @Value("${admin.seed.password}")
    private String seedPassword;

    @Override
    public void run(String... args) {
        if (userRepository.countByRoleAndEnabledTrue(Role.ADMIN) > 0) {
            return;
        }

        if (seedPassword == null || seedPassword.isBlank()) {
            log.warn("No enabled admin account exists and ADMIN_PASSWORD is not set - skipping admin seeding.");
            return;
        }

        var existing = userRepository.findByUsername(seedUsername);
        if (existing.isPresent()) {
            var user = existing.get();
            user.setRole(Role.ADMIN);
            user.setEnabled(true);
            userRepository.save(user);
            log.info("Promoted existing user '{}' to ADMIN.", seedUsername);
            return;
        }

        var admin = User.builder()
                .username(seedUsername)
                .password(passwordEncoder.encode(seedPassword))
                .role(Role.ADMIN)
                .enabled(true)
                .build();
        userRepository.save(admin);
        log.info("Seeded initial admin account '{}'.", seedUsername);
    }
}
