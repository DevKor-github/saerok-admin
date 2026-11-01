package apu.saerok_admin.web.activeusers;

import java.time.LocalDate;

/**
 * 한 패널의 조회 기간 선택 결과.
 *
 * <p>{@code startDate}, {@code endDate}가 모두 {@code null}이면 기간 제한 없이 전체를 조회한다.</p>
 */
public record ActiveUserRangeSelection(
        ActiveUserPanel panel,
        ActiveUserRangePreset preset,
        LocalDate startDate,
        LocalDate endDate
) {

    public boolean isCustom() {
        return preset.isCustom();
    }

    public static ActiveUserRangeSelection of(ActiveUserPanel panel, ActiveUserRangePreset preset, LocalDate today) {
        return preset.toWindow(today)
                .map(window -> new ActiveUserRangeSelection(panel, preset, window.startDate(), window.endDate()))
                .orElseGet(() -> new ActiveUserRangeSelection(panel, preset, null, null));
    }

    /**
     * 요청 파라미터를 패널의 프리셋 체계에 맞춰 해석한다.
     *
     * <p>직접 설정은 시작·종료일이 모두 있어야 하며, 뒤집힌 구간은 바로잡고 오늘 이후는 오늘로 잘라낸다.
     * 값이 모자라면 해당 패널의 기본 프리셋으로 되돌린다.</p>
     */
    public static ActiveUserRangeSelection resolve(
            ActiveUserPanel panel,
            String rangeParam,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate today
    ) {
        ActiveUserRangePreset requested = ActiveUserRangePreset.fromParameter(panel, rangeParam)
                .orElseGet(() -> panel.defaultRangePreset());

        boolean hasCustomDates = startDate != null || endDate != null;
        if (hasCustomDates) {
            requested = ActiveUserRangePreset.customOf(panel);
        }

        if (!requested.isCustom()) {
            return of(panel, requested, today);
        }

        if (startDate == null || endDate == null) {
            return of(panel, panel.defaultRangePreset(), today);
        }

        LocalDate effectiveStart = startDate;
        LocalDate effectiveEnd = endDate;

        if (effectiveEnd.isBefore(effectiveStart)) {
            effectiveStart = endDate;
            effectiveEnd = startDate;
        }
        if (effectiveEnd.isAfter(today)) {
            effectiveEnd = today;
        }
        if (effectiveStart.isAfter(effectiveEnd)) {
            effectiveStart = effectiveEnd;
        }

        return new ActiveUserRangeSelection(panel, requested, effectiveStart, effectiveEnd);
    }
}
