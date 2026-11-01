package apu.saerok_admin.web.view;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import apu.saerok_admin.web.activeusers.ActiveUserPanel.PanelBucket;
import apu.saerok_admin.web.view.ServiceInsightViewModel.Point;

/**
 * 활성 사용자수 탭의 화면 모델. 패널 하나가 차트 하나에 대응한다.
 */
public record ActiveUserViewModel(
        @JsonProperty("panels") List<Panel> panels
) {

    public record Panel(
            @JsonProperty("key") String key,
            @JsonProperty("metric") String metric,
            @JsonProperty("label") String label,
            @JsonProperty("description") String description,
            @JsonProperty("bucket") PanelBucket bucket,
            @JsonProperty("selectedRange") String selectedRange,
            @JsonProperty("customRange") boolean customRange,
            @JsonProperty("startDate") LocalDate startDate,
            @JsonProperty("endDate") LocalDate endDate,
            @JsonProperty("rangeOptions") List<RangeOption> rangeOptions,
            @JsonProperty("points") List<Point> points,
            @JsonProperty("hadError") boolean hadError
    ) {
    }

    public record RangeOption(
            @JsonProperty("value") String value,
            @JsonProperty("label") String label
    ) {
    }
}
