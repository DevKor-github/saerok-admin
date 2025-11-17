package apu.saerok_admin.infra.role.dto;

import java.util.List;

public record AdminRoleUserListResponse(
        List<AdminUserRoleResponse> users
) {
}
