package apu.saerok_admin.web.view;

import java.time.LocalDateTime;
import java.util.List;

public record DexListItem(long id, String koreanName, String englishName, String scientificName,
                          String habitat, String stayType, String rarity, LocalDateTime updatedAt, List<String> tags) {
}
