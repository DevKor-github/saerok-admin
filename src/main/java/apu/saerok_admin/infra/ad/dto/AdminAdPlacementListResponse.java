package apu.saerok_admin.infra.ad.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminAdPlacementListResponse(List<Item> items) {

    public record Item(
            Long id,
            Long adId,
            String adName,
            String adImageUrl,
            Long slotId,
            String slotName,
            LocalDate startDate,
            LocalDate endDate,
            Short weight,
            Boolean enabled,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}
