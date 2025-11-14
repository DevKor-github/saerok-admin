package apu.saerok_admin.infra.ad.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminAdListResponse(List<Item> items) {

    public record Item(
            Long id,
            String name,
            String memo,
            String imageUrl,
            String contentType,
            String targetUrl,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}
