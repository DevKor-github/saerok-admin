package apu.saerok_admin.web.activeusers;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import apu.saerok_admin.infra.stat.AdminStatClient;
import apu.saerok_admin.infra.stat.StatMetric;
import apu.saerok_admin.infra.stat.dto.StatSeriesResponse;
import apu.saerok_admin.web.view.ActiveUserViewModel;
import apu.saerok_admin.web.view.ActiveUserViewModel.Panel;

@ExtendWith(MockitoExtension.class)
class ActiveUserServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

    @Mock
    private AdminStatClient adminStatClient;

    @InjectMocks
    private ActiveUserService sut;

    @Test
    void loadPanel_mapsPointsInDateOrder() {
        when(adminStatClient.fetchSeries(any(), any(), any())).thenReturn(seriesOf(
                StatMetric.USER_WAU,
                point("2026-10-14", 410),
                point("2026-10-07", 380)
        ));

        Panel panel = sut.loadPanel(selection(ActiveUserPanel.WAU, "recent-4w"));

        assertThat(panel.key()).isEqualTo("wau");
        assertThat(panel.bucket()).isEqualTo(ActiveUserPanel.PanelBucket.WEEKLY);
        assertThat(panel.hadError()).isFalse();
        assertThat(panel.points()).extracting(p -> p.date().toString())
                .containsExactly("2026-10-07", "2026-10-14");
        assertThat(panel.rangeOptions()).extracting(ActiveUserViewModel.RangeOption::value)
                .contains("recent-4w", "since-last-month");
    }

    @Test
    void loadPanel_ignoresSeriesOfOtherMetrics() {
        when(adminStatClient.fetchSeries(any(), any(), any()))
                .thenReturn(seriesOf(StatMetric.USER_DAU, point("2026-10-21", 70)));

        Panel panel = sut.loadPanel(selection(ActiveUserPanel.MAU, "recent-12m"));

        assertThat(panel.points()).isEmpty();
        assertThat(panel.hadError()).isFalse();
    }

    @Test
    void loadPanel_marksErrorWithoutLosingRangeMetadata() {
        when(adminStatClient.fetchSeries(any(), any(), any()))
                .thenThrow(new RestClientException("500 Internal Server Error"));

        Panel panel = sut.loadPanel(selection(ActiveUserPanel.DAU, "recent-14d"));

        assertThat(panel.hadError()).isTrue();
        assertThat(panel.points()).isEmpty();
        assertThat(panel.selectedRange()).isEqualTo("recent-14d");
        assertThat(panel.startDate()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(panel.endDate()).isEqualTo(TODAY);
    }

    @Test
    void load_keepsHealthyPanelsWhenOneFails() {
        when(adminStatClient.fetchSeries(eq(List.of(StatMetric.USER_DAU)), any(), any()))
                .thenThrow(new RestClientException("timeout"));
        when(adminStatClient.fetchSeries(eq(List.of(StatMetric.USER_WAU)), any(), any()))
                .thenReturn(seriesOf(StatMetric.USER_WAU, point("2026-10-14", 410)));

        ActiveUserViewModel viewModel = sut.load(List.of(
                selection(ActiveUserPanel.DAU, "recent-14d"),
                selection(ActiveUserPanel.WAU, "recent-4w")
        ));

        assertThat(viewModel.panels()).hasSize(2);
        assertThat(viewModel.panels().get(0).hadError()).isTrue();
        assertThat(viewModel.panels().get(1).hadError()).isFalse();
        assertThat(viewModel.panels().get(1).points()).hasSize(1);
    }

    private ActiveUserRangeSelection selection(ActiveUserPanel panel, String range) {
        return ActiveUserRangeSelection.resolve(panel, range, null, null, TODAY);
    }

    private StatSeriesResponse seriesOf(StatMetric metric, StatSeriesResponse.Point... points) {
        return new StatSeriesResponse(List.of(
                new StatSeriesResponse.Series(metric.name(), List.of(points), List.of())
        ));
    }

    private StatSeriesResponse.Point point(String date, double value) {
        return new StatSeriesResponse.Point(LocalDate.parse(date), value);
    }
}
