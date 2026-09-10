package apu.saerok_admin.web.view;

import java.time.OffsetDateTime;
import java.util.List;

public record DexListItem(
        long id,
        String koreanName,
        String scientificName,
        String conservationGrade,
        Double bodyLengthCm,
        List<String> habitats,
        String thumbImageUrl,
        OffsetDateTime updatedAt
) {
}
