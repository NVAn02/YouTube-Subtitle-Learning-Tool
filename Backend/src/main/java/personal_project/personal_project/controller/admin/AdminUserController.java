package personal_project.personal_project.controller.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import personal_project.personal_project.dto.ErrorResponse;
import personal_project.personal_project.dto.admin.AdminUserResponse;
import personal_project.personal_project.dto.admin.UpdateRoleRequest;
import personal_project.personal_project.dto.admin.UpdateStatusRequest;
import personal_project.personal_project.entity.Role;
import personal_project.personal_project.entity.User;
import personal_project.personal_project.repository.UserRepository;
import personal_project.personal_project.service.AdminUserService;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository userRepository;
    private final AdminUserService adminUserService;

    @GetMapping
    public Page<AdminUserResponse> list(@RequestParam(defaultValue = "") String search,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        Page<User> users = search.isBlank()
                ? userRepository.findAll(PageRequest.of(page, size))
                : userRepository.findByUsernameContainingIgnoreCase(search, PageRequest.of(page, size));
        return users.map(this::toResponse);
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<?> updateRole(@PathVariable Long id,
                                         @RequestBody UpdateRoleRequest request,
                                         @AuthenticationPrincipal User actingAdmin) {
        try {
            Role newRole = Role.valueOf(request.getRole());
            User updated = adminUserService.changeRole(id, newRole, actingAdmin);
            return ResponseEntity.ok(toResponse(updated));
        } catch (AdminUserService.AdminActionException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Invalid role"));
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                           @RequestBody UpdateStatusRequest request,
                                           @AuthenticationPrincipal User actingAdmin) {
        try {
            User updated = adminUserService.setEnabled(id, request.isEnabled(), actingAdmin);
            return ResponseEntity.ok(toResponse(updated));
        } catch (AdminUserService.AdminActionException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    private AdminUserResponse toResponse(User user) {
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getRole().name(), user.isEnabled());
    }
}
