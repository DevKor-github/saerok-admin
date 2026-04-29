package apu.saerok_admin.infra.user.dto;

import java.util.List;

public record AdminUserListResponse(
        List<Item> users,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public record Item(
            Long id,
            String nickname
    ) {
    }
}
