package apu.saerok_admin.web.activeusers;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveUserRangeSelectionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    @Test
    void dauDefaultsToLastTwoWeeks() {
        ActiveUserRangeSelection selection = resolve(ActiveUserPanel.DAU, null, null, null);

        assertThat(selection.preset()).isEqualTo(ActiveUserRangePreset.DAU_LAST_14_DAYS);
        assertThat(selection.startDate()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(selection.endDate()).isEqualTo(TODAY);
    }

    @Test
    void wauDefaultsToTheFirstDayOfLastMonth() {
        ActiveUserRangeSelection selection = resolve(ActiveUserPanel.WAU, null, null, null);

        assertThat(selection.preset()).isEqualTo(ActiveUserRangePreset.WAU_SINCE_LAST_MONTH);
        assertThat(selection.startDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(selection.endDate()).isEqualTo(TODAY);
    }

    @Test
    void mauDefaultsToTwelveMonthsFromTheFirstDay() {
        ActiveUserRangeSelection selection = resolve(ActiveUserPanel.MAU, null, null, null);

        assertThat(selection.preset()).isEqualTo(ActiveUserRangePreset.MAU_LAST_12_MONTHS);
        assertThat(selection.startDate()).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(selection.endDate()).isEqualTo(TODAY);
    }

    @Test
    void mauAllPresetLeavesTheRangeOpen() {
        ActiveUserRangeSelection selection = resolve(ActiveUserPanel.MAU, "all", null, null);

        assertThat(selection.preset()).isEqualTo(ActiveUserRangePreset.MAU_ALL);
        assertThat(selection.startDate()).isNull();
        assertThat(selection.endDate()).isNull();
    }

    @Test
    void presetsOfOtherPanelsAreNotAccepted() {
        ActiveUserRangeSelection selection = resolve(ActiveUserPanel.DAU, "recent-12m", null, null);

        assertThat(selection.preset()).isEqualTo(ActiveUserRangePreset.DAU_LAST_14_DAYS);
    }

    @Test
    void customRangeIsOrderedAndClampedToToday() {
        ActiveUserRangeSelection selection = resolve(
                ActiveUserPanel.WAU,
                "custom",
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 9, 1)
        );

        assertThat(selection.isCustom()).isTrue();
        assertThat(selection.startDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(selection.endDate()).isEqualTo(TODAY);
    }

    @Test
    void incompleteCustomRangeFallsBackToTheDefaultPreset() {
        ActiveUserRangeSelection selection = resolve(ActiveUserPanel.MAU, "custom", LocalDate.of(2026, 9, 1), null);

        assertThat(selection.preset()).isEqualTo(ActiveUserRangePreset.MAU_LAST_12_MONTHS);
        assertThat(selection.startDate()).isEqualTo(LocalDate.of(2025, 12, 1));
    }

    private ActiveUserRangeSelection resolve(ActiveUserPanel panel, String range, LocalDate start, LocalDate end) {
        return ActiveUserRangeSelection.resolve(panel, range, start, end, TODAY);
    }
}
