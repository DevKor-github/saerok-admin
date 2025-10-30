package apu.saerok_admin.web.view;

import apu.saerok_admin.infra.stat.StatMetric.MetricUnit;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ServiceInsightViewModel(
        List<MetricOption> metricOptions,
        List<Series> series,
        Map<String, Map<String, String>> componentLabels
) {

    public record MetricOption(
            String metric,
            String label,
            String description,
            MetricUnit unit,
            boolean multiSeries,
            boolean defaultActive
    ) {
    }

    public record Series(
            String metric,
            List<Point> points,
            List<ComponentSeries> components
    ) {
    }

    public record Point(
            LocalDate date,
            double value
    ) {
    }

    public record ComponentSeries(
            String key,
            List<Point> points
    ) {
    }
}
