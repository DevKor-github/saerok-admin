package apu.saerok_admin.web.view.role;

import java.util.List;

public record RolePermissionGroupView(
        RoleDisplay role,
        List<PermissionView> permissions
) {

    public RolePermissionGroupView {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    public boolean hasPermissions() {
        return !permissions.isEmpty();
    }
}
