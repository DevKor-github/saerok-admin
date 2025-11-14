package apu.saerok_admin.web.view.ad;

public record AdPlacementForm(
        Long id,
        Long adId,
        Long slotId,
        String startDate,
        String endDate,
        Short weight,
        Boolean enabled
) {

    public boolean isEdit() {
        return id != null;
    }
}
