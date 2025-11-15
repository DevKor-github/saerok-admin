package apu.saerok_admin.web.serviceinsight;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum ServiceInsightRangePreset {

    LAST_7_DAYS("recent-7", "최근 1주", 7),
    LAST_14_DAYS("recent-14", "최근 2주", 14),
    LAST_30_DAYS("recent-30", "최근 1달", 30),
    ALL("all", "전체", null),
    CUSTOM("custom", "사용자 지정", null);

    private final String paramValue;
    private final String displayLabel;
    private final Integer days;

    ServiceInsightRangePreset(String paramValue, String displayLabel, Integer days) {
        this.paramValue = paramValue;
        this.displayLabel = displayLabel;
        this.days = days;
    }

    public String paramValue() {
        return paramValue;
    }

    public String displayLabel() {
        return displayLabel;
    }

    public boolean isCustom() {
        return this == CUSTOM;
    }

    public boolean isAll() {
        return this == ALL;
    }

    public Optional<RangeWindow> toWindow(LocalDate today) {
        if (days == null) {
            return Optional.empty();
        }
        if (today == null) {
            return Optional.empty();
        }
        if (days <= 0) {
            return Optional.empty();
        }
        LocalDate endDate = today;
        LocalDate startDate = today.minusDays(days - 1L);
        return Optional.of(new RangeWindow(startDate, endDate));
    }

    public static Optional<ServiceInsightRangePreset> fromParameter(String parameter) {
        if (parameter == null || parameter.isBlank()) {
            return Optional.empty();
        }
        String normalized = parameter.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(preset -> preset.paramValue.equalsIgnoreCase(normalized) || preset.name().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public static List<ServiceInsightRangePreset> quickSelections() {
        return List.of(LAST_7_DAYS, LAST_14_DAYS, LAST_30_DAYS, ALL);
    }

    public record RangeWindow(LocalDate startDate, LocalDate endDate) {
    }
}
