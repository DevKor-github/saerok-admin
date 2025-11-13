package apu.saerok_admin.web.view.ad;

import java.util.List;

public record AdPlacementGroup(
        Long slotId,
        String slotCode,
        String slotDescription,
        List<AdPlacementItem> placements
) {

    public boolean hasPlacements() {
        return placements != null && !placements.isEmpty();
    }
}
