package personal_project.personal_project.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import personal_project.personal_project.entity.Role;
import personal_project.personal_project.entity.User;
import personal_project.personal_project.repository.UserRepository;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_user_service_test",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "admin.seed.password="
})
class AdminUserServiceTest {

    @Autowired
    private AdminUserService adminUserService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User admin;
    private User plainUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        admin = userRepository.save(User.builder()
                .username("admin1").password(passwordEncoder.encode("pw")).role(Role.ADMIN).enabled(true).build());
        plainUser = userRepository.save(User.builder()
                .username("user1").password(passwordEncoder.encode("pw")).role(Role.USER).enabled(true).build());
    }

    @Test
    void changeRolePromotesUserToAdmin() {
        User updated = adminUserService.changeRole(plainUser.getId(), Role.ADMIN, admin);
        assertEquals(Role.ADMIN, updated.getRole());
    }

    @Test
    void adminCannotChangeOwnRole() {
        assertThrows(AdminUserService.AdminActionException.class,
                () -> adminUserService.changeRole(admin.getId(), Role.USER, admin));
    }

    @Test
    void lastEnabledAdminCannotBeDemoted() {
        // admin1 is the only enabled ADMIN; a second admin acts on it via a fresh User instance with a different id
        User secondAdmin = userRepository.save(User.builder()
                .username("admin2").password(passwordEncoder.encode("pw")).role(Role.ADMIN).enabled(true).build());

        // Demote admin1 (acting as admin2) - should succeed since two admins exist
        adminUserService.changeRole(admin.getId(), Role.USER, secondAdmin);

        // Now only admin2 remains an enabled admin; demoting it should fail (acting as plainUser id would be wrong,
        // simulate a third admin trying to demote the last one)
        User thirdAdmin = userRepository.save(User.builder()
                .username("admin3").password(passwordEncoder.encode("pw")).role(Role.ADMIN).enabled(true).build());
        adminUserService.changeRole(secondAdmin.getId(), Role.USER, thirdAdmin);

        assertThrows(AdminUserService.AdminActionException.class,
                () -> adminUserService.changeRole(thirdAdmin.getId(), Role.USER, admin));
    }

    @Test
    void disablingLastEnabledAdminIsBlocked() {
        User secondAdmin = userRepository.save(User.builder()
                .username("admin2b").password(passwordEncoder.encode("pw")).role(Role.ADMIN).enabled(true).build());
        adminUserService.setEnabled(admin.getId(), false, secondAdmin);

        assertThrows(AdminUserService.AdminActionException.class,
                () -> adminUserService.setEnabled(secondAdmin.getId(), false, admin));
    }

    @Test
    void adminCannotDisableSelf() {
        assertThrows(AdminUserService.AdminActionException.class,
                () -> adminUserService.setEnabled(admin.getId(), false, admin));
    }
}
