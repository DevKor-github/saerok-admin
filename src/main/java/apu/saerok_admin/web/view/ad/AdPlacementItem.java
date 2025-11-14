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
        PlacementTimeStatus timeStatus,
        double displayProbabilityPercent
) {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final double PROBABILITY_EPSILON = 1.0E-6;

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

    public AdPlacementItem withDisplayProbability(double probabilityPercent) {
        return new AdPlacementItem(
                id,
                adId,
                adName,
                adImageUrl,
                slotId,
                slotName,
                startDate,
                endDate,
                weight,
                enabled,
                createdAt,
                updatedAt,
                timeStatus,
                probabilityPercent
        );
    }

    public boolean isActiveNow() {
        return timeStatus == PlacementTimeStatus.ACTIVE;
    }

    public boolean isProbabilityEligible() {
        return isActiveNow() && enabled;
    }

    public boolean hasPositiveProbability() {
        return displayProbabilityPercent > PROBABILITY_EPSILON;
    }

    public boolean canToggle() {
        return isActiveNow();
    }

    public String displayStatusLabel() {
        if (isActiveNow()) {
            return enabled ? "진행 중" : "일시 중지";
        }
        return switch (timeStatus) {
            case UPCOMING -> "시작 예정";
            case ENDED -> "종료";
            case ACTIVE -> "진행 중";
        };
    }

    public String timeStatusLabel() {
        return switch (timeStatus) {
            case ACTIVE -> "진행 중";
            case UPCOMING -> "시작 예정";
            case ENDED -> "종료";
        };
    }

    public String timeStatusBadgeClass() {
        return switch (timeStatus) {
            case ACTIVE -> "text-bg-success";
            case UPCOMING -> "text-bg-info";
            case ENDED -> "text-bg-secondary";
        };
    }

    public String statusBadgeClass() {
        if (isActiveNow()) {
            return enabled ? "text-bg-success" : "text-bg-warning";
        }
        return switch (timeStatus) {
            case UPCOMING -> "text-bg-info";
            case ENDED -> "text-bg-secondary";
            case ACTIVE -> enabled ? "text-bg-success" : "text-bg-warning";
        };
    }

    public String weightLabel() {
        return switch (weight) {
            case 1 -> "매우 낮음";
            case 2 -> "낮음";
            case 3 -> "보통";
            case 4 -> "높음";
            case 5 -> "매우 높음";
            default -> Short.toString(weight);
        };
    }

    public boolean hasImage() {
        return adImageUrl != null && !adImageUrl.isBlank();
    }

    public String probabilityLabel() {
        return Math.round(displayProbabilityPercent) + "%";
    }
}
