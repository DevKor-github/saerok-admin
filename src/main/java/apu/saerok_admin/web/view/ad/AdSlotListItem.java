package apu.saerok_admin.web.view.ad;

import java.time.OffsetDateTime;

public record AdSlotListItem(
        Long id,
        String code,
        String description,
        double fallbackRatioPercent,
        int ttlSeconds,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        int connectedScheduleCount
) {

    public String fallbackRatioLabel() {
        return String.format("%.1f%%", fallbackRatioPercent);
    }

    public String fallbackProbabilityLabel() {
        return Math.round(fallbackRatioPercent) + "%";
    }
}
