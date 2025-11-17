package apu.saerok_admin.infra.role.dto;

import java.util.List;

public record AdminUserRoleResponse(
        Long userId,
        String nickname,
        String email,
        boolean superAdmin,
        List<RoleSummaryResponse> roles,
        List<PermissionSummaryResponse> permissions
) {
}
