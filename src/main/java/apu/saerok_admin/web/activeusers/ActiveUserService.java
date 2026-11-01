package apu.saerok_admin.web.activeusers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import apu.saerok_admin.infra.stat.AdminStatClient;
import apu.saerok_admin.infra.stat.dto.StatSeriesResponse;
import apu.saerok_admin.web.view.ActiveUserViewModel;
import apu.saerok_admin.web.view.ActiveUserViewModel.Panel;
import apu.saerok_admin.web.view.ActiveUserViewModel.RangeOption;
import apu.saerok_admin.web.view.ServiceInsightViewModel.Point;

/**
 * 활성 사용자수 패널을 패널별 기간으로 각각 조회한다.
 *
 * <p>패널마다 조회 구간이 다르므로 백엔드 호출도 패널 단위로 나누고, 한 패널이 실패해도 나머지는 그대로 그린다.</p>
 */
@Service
public class ActiveUserService {

    private static final Logger log = LoggerFactory.getLogger(ActiveUserService.class);

    private final AdminStatClient adminStatClient;

    public ActiveUserService(AdminStatClient adminStatClient) {
        this.adminStatClient = adminStatClient;
    }

    public ActiveUserViewModel load(List<ActiveUserRangeSelection> selections) {
        List<Panel> panels = new ArrayList<>();
        for (ActiveUserRangeSelection selection : selections) {
            if (selection == null) {
                continue;
            }
            panels.add(loadPanel(selection));
        }
        return new ActiveUserViewModel(List.copyOf(panels));
    }

    public Panel loadPanel(ActiveUserRangeSelection selection) {
        ActiveUserPanel panel = selection.panel();

        List<Point> points = List.of();
        boolean hadError = false;
        try {
            StatSeriesResponse response = adminStatClient.fetchSeries(
                    List.of(panel.metric()),
                    selection.startDate(),
                    selection.endDate()
            );
            points = toPoints(response, panel);
        } catch (RestClientException | IllegalStateException | IllegalArgumentException exception) {
            log.warn("Failed to load active user panel {} ({} ~ {}).",
                    panel.name(),
                    selection.startDate(),
                    selection.endDate(),
                    exception);
            hadError = true;
        }

        return new Panel(
                panel.key(),
                panel.metric().name(),
                panel.label(),
                panel.description(),
                panel.bucket(),
                selection.preset().paramValue(),
                selection.isCustom(),
                selection.startDate(),
                selection.endDate(),
                rangeOptions(panel),
                points,
                hadError
        );
    }

    private List<RangeOption> rangeOptions(ActiveUserPanel panel) {
        return panel.quickRangePresets().stream()
                .map(preset -> new RangeOption(preset.paramValue(), preset.displayLabel()))
                .toList();
    }

    private List<Point> toPoints(StatSeriesResponse response, ActiveUserPanel panel) {
        if (response == null || response.series() == null) {
            return List.of();
        }
        return response.series().stream()
                .filter(Objects::nonNull)
                .filter(series -> panel.metric().name().equals(series.metric()))
                .findFirst()
                .map(series -> series.points() == null ? List.<StatSeriesResponse.Point>of() : series.points())
                .orElseGet(List::of)
                .stream()
                .filter(point -> point != null && point.date() != null && point.value() != null)
                .sorted(Comparator.comparing(StatSeriesResponse.Point::date))
                .map(point -> new Point(point.date(), point.value().doubleValue()))
                .toList();
    }
}
