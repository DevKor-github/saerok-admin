package apu.saerok_admin.web.view;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import apu.saerok_admin.infra.stat.StatMetric.MetricUnit;

public record ServiceInsightViewModel(
        @JsonProperty("metricOptions") List<MetricOption> metricOptions,
        @JsonProperty("series") List<Series> series,
        @JsonProperty("componentLabels") Map<String, Map<String, String>> componentLabels,
        @JsonProperty("currentUserStats") CurrentUserStats currentUserStats
) {

    public record CurrentUserStats(
            @JsonProperty("completedUserCount") long completedUserCount,
            @JsonProperty("signupSources") List<CategoryCount> signupSources,
            @JsonProperty("activePushPlatforms") List<CategoryCount> activePushPlatforms
    ) {
    }

    public record CategoryCount(
            @JsonProperty("key") String key,
            @JsonProperty("label") String label,
            @JsonProperty("count") long count
    ) {
    }

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
