package personal_project.personal_project.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import personal_project.personal_project.entity.Role;
import personal_project.personal_project.entity.User;
import personal_project.personal_project.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;

    public User changeRole(Long targetId, Role newRole, User actingAdmin) {
        var target = userRepository.findById(targetId)
                .orElseThrow(() -> new AdminActionException("User not found"));

        if (target.getId().equals(actingAdmin.getId())) {
            throw new AdminActionException("Không thể tự thay đổi vai trò của chính mình");
        }
        if (isLastEnabledAdmin(target) && newRole != Role.ADMIN) {
            throw new AdminActionException("Phải còn ít nhất một tài khoản ADMIN đang hoạt động");
        }

        target.setRole(newRole);
        return userRepository.save(target);
    }

    public User setEnabled(Long targetId, boolean enabled, User actingAdmin) {
        var target = userRepository.findById(targetId)
                .orElseThrow(() -> new AdminActionException("User not found"));

        if (target.getId().equals(actingAdmin.getId())) {
            throw new AdminActionException("Không thể tự khóa/mở khóa chính mình");
        }
        if (!enabled && isLastEnabledAdmin(target)) {
            throw new AdminActionException("Phải còn ít nhất một tài khoản ADMIN đang hoạt động");
        }

        target.setEnabled(enabled);
        return userRepository.save(target);
    }

    private boolean isLastEnabledAdmin(User target) {
        return target.getRole() == Role.ADMIN
                && target.isEnabled()
                && userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1;
    }

    public static class AdminActionException extends RuntimeException {
        public AdminActionException(String message) {
            super(message);
        }
    }
}
