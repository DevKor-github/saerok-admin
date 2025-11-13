package apu.saerok_admin.infra.ad.dto;

import java.time.LocalDate;

public record AdminUpdateAdPlacementRequest(
        Long slotId,
        LocalDate startDate,
        LocalDate endDate,
        Short weight,
        Boolean enabled
) {
}
