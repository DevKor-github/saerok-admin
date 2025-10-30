package apu.saerok_admin.infra.stat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public enum StatMetric {
    COLLECTION_TOTAL_COUNT(
            "누적 새록 수",
            "지금까지 등록된 새록의 총 개수입니다.",
            MetricUnit.COUNT,
            false,
            Map.of(),
            true
    ),
    COLLECTION_PRIVATE_RATIO(
            "비공개 새록 비율",
            "전체 새록 중 비공개로 설정된 비율입니다.",
            MetricUnit.RATIO,
            false,
            Map.of(),
            false
    ),
    BIRD_ID_PENDING_COUNT(
            "진행 중인 동정 요청",
            "아직 해결되지 않은 동정 요청 수입니다.",
            MetricUnit.COUNT,
            false,
            Map.of(),
            false
    ),
    BIRD_ID_RESOLVED_COUNT(
            "누적 동정 해결 수",
            "동정 제안이 채택되어 해결된 요청의 누적 개수입니다.",
            MetricUnit.COUNT,
            false,
            Map.of(),
            true
    ),
    BIRD_ID_RESOLUTION_STATS(
            "동정 해결 시간",
            "동정 요청이 해결되기까지 걸린 시간 통계입니다.",
            MetricUnit.HOURS,
            true,
            orderedComponentLabels(),
            false
    );

    private final String label;
    private final String description;
    private final MetricUnit unit;
    private final boolean multiSeries;
    private final Map<String, String> componentLabels;
    private final boolean defaultActive;

    StatMetric(
            String label,
            String description,
            MetricUnit unit,
            boolean multiSeries,
            Map<String, String> componentLabels,
            boolean defaultActive
    ) {
        this.label = label;
        this.description = description;
        this.unit = unit;
        this.multiSeries = multiSeries;
        this.componentLabels = componentLabels;
        this.defaultActive = defaultActive;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public MetricUnit unit() {
        return unit;
    }

    public boolean multiSeries() {
        return multiSeries;
    }

    public Map<String, String> componentLabels() {
        return componentLabels;
    }

    public boolean defaultActive() {
        return defaultActive;
    }

    private static Map<String, String> orderedComponentLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("min_hours", "최소");
        labels.put("max_hours", "최대");
        labels.put("avg_hours", "평균");
        labels.put("stddev_hours", "표준편차");
        return Collections.unmodifiableMap(labels);
    }

    public enum MetricUnit {
        COUNT,
        RATIO,
        HOURS
    }
}
