package apu.saerok_admin.web.view.ad;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public record AdPlacementItem(
        Long id,
        Long adId,
        String adName,
        String adImageUrl,
        Long slotId,
        String slotName,
        LocalDate startDate,
        LocalDate endDate,
        short weight,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        PlacementTimeStatus timeStatus
) {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    public String periodLabel() {
        // 시작/종료일이 모두 없는 경우
        if (startDate == null && endDate == null) {
            return "기간 미지정";
        }

        // 시작일만 없는 경우: 열린 시작
        if (startDate == null) {
            return "~ " + PERIOD_FORMATTER.format(endDate);
        }

        // 종료일만 없는 경우: 열린 종료
        if (endDate == null) {
            return PERIOD_FORMATTER.format(startDate) + " ~";
        }

        // 둘 다 있는 경우
        return PERIOD_FORMATTER.format(startDate) + " ~ " + PERIOD_FORMATTER.format(endDate);
    }

    public String displayStatusLabel() {
        return enabled ? "노출 중" : "일시 중지";
    }

    public String timeStatusLabel() {
        return switch (timeStatus) {
            case ACTIVE -> "노출 중";
            case UPCOMING -> "예정";
            case ENDED -> "종료";
        };
    }

    public String weightLabel() {
        return Short.toString(weight);
    }

    public boolean hasImage() {
        return adImageUrl != null && !adImageUrl.isBlank();
    }
}
