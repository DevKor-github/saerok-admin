package apu.saerok_admin.infra.ad.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminSlotListResponse(List<Item> items) {

    public record Item(
            Long id,
            String name,
            String memo,
            Double fallbackRatio,
            Integer ttlSeconds,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}
