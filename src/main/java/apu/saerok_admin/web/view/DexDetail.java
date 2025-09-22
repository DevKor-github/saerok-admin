package apu.saerok_admin.web.view;

import java.time.LocalDateTime;
import java.util.List;

public record DexDetail(long id, String koreanName, String englishName, String scientificName,
                        String description, String imageUrl, List<String> habitats, String stayType, String rarity,
                        List<String> tags, LocalDateTime updatedAt) {
}
