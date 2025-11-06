package apu.saerok_admin.infra.stat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public enum StatMetric {
    COLLECTION_TOTAL_COUNT(
            "새록 총 개수",
            "등록된 새록의 총 개수",
            MetricUnit.COUNT,
            false,
            Map.of(),
            true
    ),
    COLLECTION_PRIVATE_RATIO(
            "비공개 새록 비율",
            "전체 새록 중 비공개로 설정된 비율",
            MetricUnit.RATIO,
            false,
            Map.of(),
            false
    ),
    BIRD_ID_PENDING_COUNT(
            "진행 중인 동정 요청 개수",
            "이 날 진행 중인 동정 요청의 개수\n(= \"이름 모를 새 새록\"의 개수)",
            MetricUnit.COUNT,
            false,
            Map.of(),
            false
    ),
    BIRD_ID_RESOLVED_COUNT(
            "동정 의견 채택 횟수",
            "이 날 동정 의견이 몇 번 채택됐는지 횟수",
            MetricUnit.COUNT,
            false,
            Map.of(),
            true
    ),
    BIRD_ID_RESOLUTION_STATS(
            "동정 의견 채택 시간",
            "동정 요청 후 채택되기까지 평균적으로 걸린 시간",
            MetricUnit.HOURS,
            true,
            orderedComponentLabels(),
            false
    ),

    // ===== 유저 지표 =====
    USER_COMPLETED_TOTAL(
            "누적 가입자 수",
            "현재 가입된 총 사용자 수",
            MetricUnit.COUNT,
            false,
            Map.of(),
            true
    ),
    USER_SIGNUP_DAILY(
            "일일 가입자 수",
            "이 날 신규 가입한 사용자 수",
            MetricUnit.COUNT,
            false,
            Map.of(),
            false
    ),
    USER_WITHDRAWAL_DAILY(
            "일일 탈퇴자 수",
            "이 날 탈퇴한 사용자 수",
            MetricUnit.COUNT,
            false,
            Map.of(),
            false
    ),
    USER_DAU(
            "DAU",
            "일일 활성 사용자 수(그 날 서비스에 몇 명이 접속했는지 기준)",
            MetricUnit.COUNT,
            false,
            Map.of(),
            true
    ),
    USER_WAU(
            "WAU",
            "주간 활성 사용자 수(최근 7일)",
            MetricUnit.COUNT,
            false,
            Map.of(),
            false
    ),
    USER_MAU(
            "MAU",
            "월간 활성 사용자 수(최근 30일)",
            MetricUnit.COUNT,
            false,
            Map.of(),
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

    public String label() { return label; }
    public String description() { return description; }
    public MetricUnit unit() { return unit; }
    public boolean multiSeries() { return multiSeries; }
    public Map<String, String> componentLabels() { return componentLabels; }
    public boolean defaultActive() { return defaultActive; }

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
