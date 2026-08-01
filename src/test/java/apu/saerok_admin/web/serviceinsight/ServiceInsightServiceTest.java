package apu.saerok_admin.web.serviceinsight;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import apu.saerok_admin.infra.stat.AdminStatClient;
import apu.saerok_admin.infra.stat.dto.CurrentUserStatResponse;
import apu.saerok_admin.infra.stat.dto.StatSeriesResponse;
import apu.saerok_admin.web.view.ServiceInsightViewModel;

@ExtendWith(MockitoExtension.class)
class ServiceInsightServiceTest {

    @Mock
    private AdminStatClient adminStatClient;

    @InjectMocks
    private ServiceInsightService sut;

    @Test
    void loadViewModel_keepsTimeSeriesAvailableWhenCurrentUserStatsEndpointIsNotDeployedYet() {
        when(adminStatClient.fetchSeries(any(), any(), any()))
                .thenReturn(new StatSeriesResponse(List.of()));
        when(adminStatClient.fetchCurrentUserStats())
                .thenThrow(new RestClientException("404 Not Found"));

        ServiceInsightViewModel viewModel = sut.loadViewModel(ServiceInsightQuery.all());

        assertThat(viewModel.metricOptions()).isNotEmpty();
        assertThat(viewModel.currentUserStats()).isNull();
    }

    @Test
    void loadViewModel_mapsCurrentUserStatsForTheSeparateStatusSection() {
        when(adminStatClient.fetchSeries(any(), any(), any()))
                .thenReturn(new StatSeriesResponse(List.of()));
        when(adminStatClient.fetchCurrentUserStats()).thenReturn(new CurrentUserStatResponse(
                4L,
                Map.of("INSTAGRAM", 2L, "UNKNOWN", 1L),
                Map.of("IOS", 2L, "ANDROID", 1L)
        ));

        ServiceInsightViewModel viewModel = sut.loadViewModel(ServiceInsightQuery.all());

        assertThat(viewModel.currentUserStats().completedUserCount()).isEqualTo(4L);
        assertThat(viewModel.currentUserStats().signupSources())
                .extracting(ServiceInsightViewModel.CategoryCount::label)
                .contains("인스타그램", "미기록");
        assertThat(viewModel.currentUserStats().activePushPlatforms())
                .extracting(ServiceInsightViewModel.CategoryCount::label)
                .contains("iOS", "Android");
    }
}
