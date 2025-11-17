package apu.saerok_admin.web.view.role;

import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

public record TeamMemberView(
        Long id,
        String nickname,
        String email,
        boolean superAdmin,
        List<RoleDisplay> roles,
        List<PermissionView> permissions
) {

    public TeamMemberView {
        roles = roles == null ? List.of() : List.copyOf(roles);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    public String initials() {
        if (!StringUtils.hasText(nickname)) {
            return "팀";
        }
        String trimmed = nickname.trim();
        return trimmed.substring(0, Math.min(2, trimmed.length()));
    }

    public boolean hasRole(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return false;
        }
        String normalized = roleCode.trim().toUpperCase(Locale.ROOT);
        return roles.stream().anyMatch(role -> normalized.equals(role.code()));
    }

    public boolean hasPermissions() {
        return !permissions.isEmpty();
    }
}
