package apu.saerok_admin.web.view.ad;

import java.util.List;

public record AdPlacementGroup(
        Long slotId,
        String slotCode,
        String slotDescription,
        double fallbackRatioPercent,
        int slotTtlSeconds,
        List<AdPlacementItem> placements
) {

    public boolean hasPlacements() {
        return placements != null && !placements.isEmpty();
    }

    public String fallbackProbabilityLabel() {
        return Math.round(fallbackRatioPercent) + "%";
    }

    public boolean hasFallbackProbability() {
        return fallbackRatioPercent > 0.0;
    }
}
