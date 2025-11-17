package apu.saerok_admin.web.view.role;

import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

public record RoleTemplateView(
        RoleDisplay role,
        List<PermissionView> permissions
) {

    public RoleTemplateView {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    public Long id() {
        return role.id();
    }

    public String code() {
        return role.code();
    }

    public String displayName() {
        return role.displayName();
    }

    public String description() {
        return role.description();
    }

    public boolean builtin() {
        return role.builtin();
    }

    public boolean hasPermissions() {
        return !permissions.isEmpty();
    }

    public boolean hasPermission(String permissionKey) {
        if (!StringUtils.hasText(permissionKey)) {
            return false;
        }
        String normalized = permissionKey.trim().toUpperCase(Locale.ROOT);
        return permissions.stream().anyMatch(permission -> normalized.equals(permission.key()));
    }
}
