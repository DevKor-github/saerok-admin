package apu.saerok_admin.infra.role.dto;

public record RoleSummaryResponse(
        Long id,
        String code,
        String displayName,
        String description,
        boolean builtin
) {
}
