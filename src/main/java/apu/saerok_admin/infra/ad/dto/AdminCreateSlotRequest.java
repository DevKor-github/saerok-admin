package apu.saerok_admin.infra.ad.dto;

public record AdminCreateSlotRequest(
        String name,
        String memo,
        Double fallbackRatio,
        Integer ttlSeconds
) {
}
