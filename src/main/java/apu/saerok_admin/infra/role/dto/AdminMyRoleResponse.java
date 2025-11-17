package apu.saerok_admin.infra.role.dto;

import java.util.List;

public record AdminMyRoleResponse(
        List<RoleSummaryResponse> roles,
        List<PermissionSummaryResponse> permissions
) {
}
