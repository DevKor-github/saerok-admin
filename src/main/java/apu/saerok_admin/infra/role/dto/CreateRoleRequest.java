package apu.saerok_admin.infra.role.dto;

public record CreateRoleRequest(
        String code,
        String displayName,
        String description
) {
}
