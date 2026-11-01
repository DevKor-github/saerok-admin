package apu.saerok_admin.web.activeusers;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 패널별 조회 기간 프리셋.
 *
 * <p>DAU는 일 단위, WAU는 주 단위, MAU는 월 단위로 묶어서 보기 때문에 프리셋도 패널마다 따로 둔다.
 * 같은 {@code paramValue}가 패널별로 중복될 수 있으므로 항상 패널과 함께 조회한다.</p>
 */
public enum ActiveUserRangePreset {

    // DAU: 최근 흐름만 일 단위로 본다.
    DAU_LAST_7_DAYS(ActiveUserPanel.DAU, "recent-7d", "최근 1주", WindowKind.DAYS, 7, false),
    DAU_LAST_14_DAYS(ActiveUserPanel.DAU, "recent-14d", "최근 2주", WindowKind.DAYS, 14, true),
    DAU_LAST_30_DAYS(ActiveUserPanel.DAU, "recent-30d", "최근 1달", WindowKind.DAYS, 30, false),
    DAU_CUSTOM(ActiveUserPanel.DAU, "custom", "직접 설정", WindowKind.CUSTOM, 0, false),

    // WAU: 주 막대가 의미를 가지도록 기본값부터 지난달을 포함한다.
    WAU_LAST_4_WEEKS(ActiveUserPanel.WAU, "recent-4w", "최근 4주", WindowKind.WEEKS, 4, false),
    WAU_SINCE_LAST_MONTH(ActiveUserPanel.WAU, "since-last-month", "지난달부터", WindowKind.MONTHS_FROM_START, 2, true),
    WAU_LAST_3_MONTHS(ActiveUserPanel.WAU, "recent-3m", "최근 3개월", WindowKind.MONTHS_FROM_START, 3, false),
    WAU_LAST_6_MONTHS(ActiveUserPanel.WAU, "recent-6m", "최근 6개월", WindowKind.MONTHS_FROM_START, 6, false),
    WAU_CUSTOM(ActiveUserPanel.WAU, "custom", "직접 설정", WindowKind.CUSTOM, 0, false),

    // MAU: 월 막대라서 기본값부터 1년을 본다.
    MAU_LAST_6_MONTHS(ActiveUserPanel.MAU, "recent-6m", "최근 6개월", WindowKind.MONTHS_FROM_START, 6, false),
    MAU_LAST_12_MONTHS(ActiveUserPanel.MAU, "recent-12m", "최근 1년", WindowKind.MONTHS_FROM_START, 12, true),
    MAU_LAST_24_MONTHS(ActiveUserPanel.MAU, "recent-24m", "최근 2년", WindowKind.MONTHS_FROM_START, 24, false),
    MAU_ALL(ActiveUserPanel.MAU, "all", "전체", WindowKind.ALL, 0, false),
    MAU_CUSTOM(ActiveUserPanel.MAU, "custom", "직접 설정", WindowKind.CUSTOM, 0, false);

    private final ActiveUserPanel panel;
    private final String paramValue;
    private final String displayLabel;
    private final WindowKind windowKind;
    private final int amount;
    private final boolean defaultPreset;

    ActiveUserRangePreset(
            ActiveUserPanel panel,
            String paramValue,
            String displayLabel,
            WindowKind windowKind,
            int amount,
            boolean defaultPreset
    ) {
        this.panel = panel;
        this.paramValue = paramValue;
        this.displayLabel = displayLabel;
        this.windowKind = windowKind;
        this.amount = amount;
        this.defaultPreset = defaultPreset;
    }

    public ActiveUserPanel panel() {
        return panel;
    }

    public String paramValue() {
        return paramValue;
    }

    public String displayLabel() {
        return displayLabel;
    }

    public boolean isAll() {
        return windowKind == WindowKind.ALL;
    }

    public boolean isCustom() {
        return windowKind == WindowKind.CUSTOM;
    }

    /**
     * 프리셋이 가리키는 조회 구간. 전체·직접 설정은 구간을 스스로 정하지 않으므로 비어 있다.
     */
    public Optional<RangeWindow> toWindow(LocalDate today) {
        if (today == null || windowKind == WindowKind.ALL || windowKind == WindowKind.CUSTOM) {
            return Optional.empty();
        }
        if (amount <= 0) {
            return Optional.empty();
        }
        LocalDate startDate = switch (windowKind) {
            case DAYS -> today.minusDays(amount - 1L);
            case WEEKS -> today.minusDays(amount * 7L - 1L);
            case MONTHS_FROM_START -> today.minusMonths(amount - 1L).withDayOfMonth(1);
            default -> null;
        };
        if (startDate == null) {
            return Optional.empty();
        }
        return Optional.of(new RangeWindow(startDate, today));
    }

    public static List<ActiveUserRangePreset> quickPresetsOf(ActiveUserPanel panel) {
        return Arrays.stream(values())
                .filter(preset -> preset.panel == panel)
                .filter(preset -> !preset.isCustom())
                .toList();
    }

    public static ActiveUserRangePreset defaultOf(ActiveUserPanel panel) {
        return Arrays.stream(values())
                .filter(preset -> preset.panel == panel && preset.defaultPreset)
                .findFirst()
                .orElseGet(() -> quickPresetsOf(panel).getFirst());
    }

    public static ActiveUserRangePreset customOf(ActiveUserPanel panel) {
        return Arrays.stream(values())
                .filter(preset -> preset.panel == panel && preset.isCustom())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No custom preset for panel " + panel));
    }

    public static Optional<ActiveUserRangePreset> fromParameter(ActiveUserPanel panel, String parameter) {
        if (panel == null || parameter == null || parameter.isBlank()) {
            return Optional.empty();
        }
        String normalized = parameter.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(preset -> preset.panel == panel)
                .filter(preset -> preset.paramValue.equalsIgnoreCase(normalized)
                        || preset.name().equalsIgnoreCase(normalized))
                .findFirst();
    }

    private enum WindowKind {
        DAYS,
        WEEKS,
        MONTHS_FROM_START,
        ALL,
        CUSTOM
    }

    public record RangeWindow(LocalDate startDate, LocalDate endDate) {
    }
}
