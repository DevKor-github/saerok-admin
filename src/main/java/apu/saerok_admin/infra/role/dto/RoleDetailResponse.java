package apu.saerok_admin.infra.role.dto;

import java.util.List;

public record RoleDetailResponse(
        Long id,
        String code,
        String displayName,
        String description,
        boolean builtin,
        List<PermissionSummaryResponse> permissions
) {
}
