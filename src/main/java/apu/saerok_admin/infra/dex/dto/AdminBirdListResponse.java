package apu.saerok_admin.infra.dex.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminBirdListResponse(
        List<Item> birds,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public record Item(
            Long id,
            String koreanName,
            String scientificName,
            String conservationGrade,
            Double bodyLengthCm,
            List<String> habitats,
            String thumbImageUrl,
            OffsetDateTime updatedAt
    ) {
    }
}
