package personal_project.personal_project.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import personal_project.personal_project.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
}
