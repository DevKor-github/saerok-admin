package apu.saerok_admin.web.serviceinsight;

import apu.saerok_admin.infra.stat.AdminStatClient;
import apu.saerok_admin.infra.stat.StatMetric;
import apu.saerok_admin.infra.stat.dto.StatSeriesResponse;
import apu.saerok_admin.web.view.ServiceInsightViewModel;
import apu.saerok_admin.web.view.ServiceInsightViewModel.ComponentSeries;
import apu.saerok_admin.web.view.ServiceInsightViewModel.MetricOption;
import apu.saerok_admin.web.view.ServiceInsightViewModel.Point;
import apu.saerok_admin.web.view.ServiceInsightViewModel.Series;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ServiceInsightService {

    private final AdminStatClient adminStatClient;

    public ServiceInsightService(AdminStatClient adminStatClient) {
        this.adminStatClient = adminStatClient;
    }

    public ServiceInsightViewModel loadViewModel() {
        StatSeriesResponse response = adminStatClient.fetchSeries(List.of(StatMetric.values()));
        return buildViewModel(response);
    }

    public ServiceInsightViewModel defaultViewModel() {
        return buildViewModel(null);
    }

    private ServiceInsightViewModel buildViewModel(StatSeriesResponse response) {
        Map<String, StatSeriesResponse.Series> responseMap = Optional.ofNullable(response)
                .map(StatSeriesResponse::series)
                .orElseGet(List::of)
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        StatSeriesResponse.Series::metric,
                        series -> series,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));

        List<MetricOption> metricOptions = new ArrayList<>();
        List<Series> chartSeries = new ArrayList<>();
        Map<String, Map<String, String>> componentLabels = new LinkedHashMap<>();

        for (StatMetric metric : StatMetric.values()) {
            metricOptions.add(toMetricOption(metric));
            chartSeries.add(toSeries(metric, responseMap.get(metric.name())));
            if (metric.multiSeries()) {
                componentLabels.put(metric.name(), metric.componentLabels());
            }
        }

        return new ServiceInsightViewModel(metricOptions, chartSeries, componentLabels);
    }

    private MetricOption toMetricOption(StatMetric metric) {
        return new MetricOption(
                metric.name(),
                metric.label(),
                metric.description(),
                metric.unit(),
                metric.multiSeries(),
                metric.defaultActive()
        );
    }

    private Series toSeries(StatMetric metric, StatSeriesResponse.Series source) {
        List<Point> points = new ArrayList<>();
        List<ComponentSeries> components = new ArrayList<>();

        if (source != null) {
            if (source.points() != null) {
                for (StatSeriesResponse.Point point : source.points()) {
                    if (point == null || point.date() == null || point.value() == null) {
                        continue;
                    }
                    points.add(new Point(point.date(), point.value().doubleValue()));
                }
            }
            if (source.components() != null) {
                for (StatSeriesResponse.ComponentSeries component : source.components()) {
                    if (component == null || component.key() == null) {
                        continue;
                    }
                    List<Point> componentPoints = new ArrayList<>();
                    if (component.points() != null) {
                        for (StatSeriesResponse.Point point : component.points()) {
                            if (point == null || point.date() == null || point.value() == null) {
                                continue;
                            }
                            componentPoints.add(new Point(point.date(), point.value().doubleValue()));
                        }
                    }
                    components.add(new ComponentSeries(component.key(), componentPoints));
                }
            }
        }

        if (!metric.multiSeries()) {
            components = List.of();
        }

        return new Series(metric.name(), points, components);
    }
}
