package apu.saerok_admin.web.serviceinsight;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import apu.saerok_admin.infra.stat.AdminStatClient;
import apu.saerok_admin.infra.stat.StatMetric;
import apu.saerok_admin.infra.stat.dto.CurrentUserStatResponse;
import apu.saerok_admin.infra.stat.dto.StatSeriesResponse;
import apu.saerok_admin.web.view.ServiceInsightViewModel;
import apu.saerok_admin.web.view.ServiceInsightViewModel.CategoryCount;
import apu.saerok_admin.web.view.ServiceInsightViewModel.ComponentSeries;
import apu.saerok_admin.web.view.ServiceInsightViewModel.CurrentUserStats;
import apu.saerok_admin.web.view.ServiceInsightViewModel.MetricOption;
import apu.saerok_admin.web.view.ServiceInsightViewModel.Point;
import apu.saerok_admin.web.view.ServiceInsightViewModel.Series;

@Service
public class ServiceInsightService {

    private static final Map<String, String> SIGNUP_SOURCE_LABELS = Map.of(
            "INSTAGRAM", "인스타그램",
            "OTHER_SNS", "기타 SNS",
            "FRIEND", "지인 추천",
            "COMMUNITY", "커뮤니티",
            "ETC", "기타",
            "UNKNOWN", "미기록"
    );
    private static final Map<String, String> PLATFORM_LABELS = Map.of(
            "IOS", "iOS",
            "ANDROID", "Android"
    );

    private final AdminStatClient adminStatClient;

    public ServiceInsightService(AdminStatClient adminStatClient) {
        this.adminStatClient = adminStatClient;
    }

    public ServiceInsightViewModel loadViewModel() {
        return loadViewModel(ServiceInsightQuery.all());
    }

    public ServiceInsightViewModel loadViewModel(ServiceInsightQuery query) {
        LocalDate startDate = query != null ? query.startDate() : null;
        LocalDate endDate = query != null ? query.endDate() : null;
        StatSeriesResponse response = adminStatClient.fetchSeries(List.of(StatMetric.values()), startDate, endDate);
        CurrentUserStatResponse currentUserStats = fetchCurrentUserStatsIfAvailable();
        return buildViewModel(response, currentUserStats);
    }

    public ServiceInsightViewModel defaultViewModel() {
        return buildViewModel(null, null);
    }

    public CurrentUserStats loadCurrentUserStats() {
        return toCurrentUserStats(fetchCurrentUserStatsIfAvailable());
    }

    private CurrentUserStatResponse fetchCurrentUserStatsIfAvailable() {
        try {
            return adminStatClient.fetchCurrentUserStats();
        } catch (RestClientException | IllegalStateException ignored) {
            // 새 현황 API가 아직 배포되지 않은 백엔드와의 순차 배포를 지원한다.
            return null;
        }
    }

    private ServiceInsightViewModel buildViewModel(StatSeriesResponse response, CurrentUserStatResponse currentUserStats) {
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

        return new ServiceInsightViewModel(
                metricOptions,
                chartSeries,
                componentLabels,
                toCurrentUserStats(currentUserStats)
        );
    }

    private CurrentUserStats toCurrentUserStats(CurrentUserStatResponse source) {
        if (source == null) {
            return null;
        }
        return new CurrentUserStats(
                source.completedUserCount(),
                toCategoryCounts(source.signupSourceCounts(), SIGNUP_SOURCE_LABELS),
                toCategoryCounts(source.activePushUserCountsByPlatform(), PLATFORM_LABELS)
        );
    }

    private List<CategoryCount> toCategoryCounts(Map<String, Long> source, Map<String, String> labels) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.entrySet().stream()
                .map(entry -> new CategoryCount(
                        entry.getKey(),
                        labels.getOrDefault(entry.getKey(), entry.getKey()),
                        entry.getValue() != null ? entry.getValue() : 0L
                ))
                .toList();
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
