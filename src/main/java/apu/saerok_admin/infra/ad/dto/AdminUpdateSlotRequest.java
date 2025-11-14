package apu.saerok_admin.infra.ad.dto;

public record AdminUpdateSlotRequest(
        String memo,
        Double fallbackRatio,
        Integer ttlSeconds
) {
}
