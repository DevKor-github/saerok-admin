package apu.saerok_admin.web.view;

import java.time.LocalDate;

public record RecentDexEntry(long id, String koreanName, String englishName, LocalDate updatedAt) {
}
