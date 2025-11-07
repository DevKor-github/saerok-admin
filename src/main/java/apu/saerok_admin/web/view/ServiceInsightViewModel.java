package apu.saerok_admin.web.view;

import apu.saerok_admin.infra.stat.StatMetric.MetricUnit;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ServiceInsightViewModel(
        @JsonProperty("metricOptions") List<MetricOption> metricOptions,
        @JsonProperty("series") List<Series> series,
        @JsonProperty("componentLabels") Map<String, Map<String, String>> componentLabels
) {

    public record MetricOption(
            @JsonProperty("metric") String metric,
            @JsonProperty("label") String label,
            @JsonProperty("description") String description,
            @JsonProperty("unit") MetricUnit unit,
            @JsonProperty("multiSeries") boolean multiSeries,
            @JsonProperty("defaultActive") boolean defaultActive
    ) {
    }

    public record Series(
            @JsonProperty("metric") String metric,
            @JsonProperty("points") List<Point> points,
            @JsonProperty("components") List<ComponentSeries> components
    ) {
    }

    public record Point(
            @JsonProperty("date") LocalDate date,
            @JsonProperty("value") double value
    ) {
    }

    public record ComponentSeries(
            @JsonProperty("key") String key,
            @JsonProperty("points") List<Point> points
    ) {
    }
}