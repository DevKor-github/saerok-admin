package apu.saerok_admin.web.activeusers;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import apu.saerok_admin.infra.stat.StatMetric;

/**
 * 활성 사용자수 탭에 상시 노출되는 패널.
 *
 * <p>패널마다 x축 집계 단위가 달라서 기본 조회 기간과 조회 필터도 따로 설계한다.
 * 시계열 탭처럼 선택한 지표에 따라 플롯이 바뀌지 않고, 세 패널이 항상 같은 자리에 그려진다.</p>
 */
public enum ActiveUserPanel {

    DAU("dau", StatMetric.USER_DAU, PanelBucket.DAILY),
    WAU("wau", StatMetric.USER_WAU, PanelBucket.WEEKLY),
    MAU("mau", StatMetric.USER_MAU, PanelBucket.MONTHLY);

    private final String key;
    private final StatMetric metric;
    private final PanelBucket bucket;

    ActiveUserPanel(String key, StatMetric metric, PanelBucket bucket) {
        this.key = key;
        this.metric = metric;
        this.bucket = bucket;
    }

    public String key() {
        return key;
    }

    public StatMetric metric() {
        return metric;
    }

    public PanelBucket bucket() {
        return bucket;
    }

    public String label() {
        return metric.label();
    }

    public String description() {
        return metric.description();
    }

    public List<ActiveUserRangePreset> quickRangePresets() {
        return ActiveUserRangePreset.quickPresetsOf(this);
    }

    public ActiveUserRangePreset defaultRangePreset() {
        return ActiveUserRangePreset.defaultOf(this);
    }

    public static List<ActiveUserPanel> displayOrder() {
        return List.of(DAU, WAU, MAU);
    }

    public static Optional<ActiveUserPanel> fromKey(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (ActiveUserPanel panel : values()) {
            if (panel.key.equals(normalized) || panel.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(panel);
            }
        }
        return Optional.empty();
    }

    /**
     * 패널을 그릴 때 사용하는 x축 집계 단위.
     */
    public enum PanelBucket {
        DAILY,
        WEEKLY,
        MONTHLY
    }
}
