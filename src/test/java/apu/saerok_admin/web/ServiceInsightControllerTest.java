package apu.saerok_admin.web;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import apu.saerok_admin.infra.CurrentAdminClient;
import apu.saerok_admin.infra.stat.StatMetric.MetricUnit;
import apu.saerok_admin.security.LoginSessionManager;
import apu.saerok_admin.web.serviceinsight.ServiceInsightService;
import apu.saerok_admin.web.view.ServiceInsightViewModel;
import apu.saerok_admin.web.view.ServiceInsightViewModel.CategoryCount;
import apu.saerok_admin.web.view.ServiceInsightViewModel.CurrentUserStats;

@WebMvcTest(ServiceInsightController.class)
@AutoConfigureMockMvc(addFilters = false)
class ServiceInsightControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServiceInsightService serviceInsightService;

    @MockBean
    private CurrentAdminClient currentAdminClient;

    @MockBean
    private LoginSessionManager loginSessionManager;

    @Test
    void serviceInsight_rendersTimeSeriesPage() throws Exception {
        given(serviceInsightService.loadViewModel(any())).willReturn(viewModel());

        mockMvc.perform(get("/service-insight/time-series").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(view().name("service-insight/index"))
                .andExpect(content().string(containsString("기간")));
    }

    @Test
    void subscriberStatus_rendersCurrentUserStats() throws Exception {
        given(serviceInsightService.loadCurrentUserStats()).willReturn(viewModel().currentUserStats());

        mockMvc.perform(get("/service-insight/subscriber-status").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(view().name("service-insight/subscriber-status"))
                .andExpect(content().string(containsString("가입자 현황")))
                .andExpect(content().string(containsString("플랫폼별 활성 푸시 사용자")))
                .andExpect(content().string(containsString("복수 플랫폼은 중복 포함")));
    }

    private ServiceInsightViewModel viewModel() {
        return new ServiceInsightViewModel(
                List.of(new ServiceInsightViewModel.MetricOption(
                        "USER_DAU", "DAU", "일일 활성 사용자", MetricUnit.COUNT, false, true
                )),
                List.of(),
                Map.of(),
                new CurrentUserStats(
                        4L,
                        List.of(new CategoryCount("INSTAGRAM", "인스타그램", 2L)),
                        List.of(
                                new CategoryCount("IOS", "iOS", 2L),
                                new CategoryCount("ANDROID", "Android", 1L)
                        )
                )
        );
    }
}
