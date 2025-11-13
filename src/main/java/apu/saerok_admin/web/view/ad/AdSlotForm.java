package apu.saerok_admin.web.view.ad;

public record AdSlotForm(
        Long id,
        String code,
        String description,
        Double fallbackRatioPercent,
        Integer ttlSeconds
) {

    public boolean isEdit() {
        return id != null;
    }
}
