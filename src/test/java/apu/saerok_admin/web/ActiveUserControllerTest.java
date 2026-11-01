package apu.saerok_admin.web;

import java.time.LocalDate;
import java.util.List;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import apu.saerok_admin.infra.CurrentAdminClient;
import apu.saerok_admin.security.LoginSessionManager;
import apu.saerok_admin.web.activeusers.ActiveUserPanel.PanelBucket;
import apu.saerok_admin.web.activeusers.ActiveUserService;
import apu.saerok_admin.web.view.ActiveUserViewModel;
import apu.saerok_admin.web.view.ActiveUserViewModel.Panel;
import apu.saerok_admin.web.view.ActiveUserViewModel.RangeOption;
import apu.saerok_admin.web.view.ServiceInsightViewModel.Point;

@WebMvcTest(ActiveUserController.class)
@AutoConfigureMockMvc(addFilters = false)
class ActiveUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ActiveUserService activeUserService;

    @MockBean
    private CurrentAdminClient currentAdminClient;

    @MockBean
    private LoginSessionManager loginSessionManager;

    @Test
    void activeUsers_rendersAllThreePanelsAtOnce() throws Exception {
        given(activeUserService.load(any())).willReturn(new ActiveUserViewModel(List.of(
                panel("dau", "USER_DAU", "DAU", PanelBucket.DAILY, "recent-14d"),
                panel("wau", "USER_WAU", "WAU", PanelBucket.WEEKLY, "since-last-month"),
                panel("mau", "USER_MAU", "MAU", PanelBucket.MONTHLY, "recent-12m")
        )));

        mockMvc.perform(get("/service-insight/active-users").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(view().name("service-insight/active-users"))
                .andExpect(content().string(containsString("활성 사용자수")))
                .andExpect(content().string(containsString("data-panel=\"dau\"")))
                .andExpect(content().string(containsString("data-panel=\"wau\"")))
                .andExpect(content().string(containsString("data-panel=\"mau\"")))
                .andExpect(content().string(containsString("주별")))
                .andExpect(content().string(containsString("월별")));
    }

    @Test
    void activeUserPanel_returnsRequestedPanelOnly() throws Exception {
        given(activeUserService.loadPanel(any()))
                .willReturn(panel("wau", "USER_WAU", "WAU", PanelBucket.WEEKLY, "recent-4w"));

        mockMvc.perform(get("/service-insight/active-users/panel")
                        .param("panel", "wau")
                        .param("range", "recent-4w")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("wau"))
                .andExpect(jsonPath("$.bucket").value("WEEKLY"))
                .andExpect(jsonPath("$.selectedRange").value("recent-4w"))
                .andExpect(jsonPath("$.points[0].date").value("2026-10-21"));
    }

    @Test
    void activeUserPanel_rejectsUnknownPanel() throws Exception {
        mockMvc.perform(get("/service-insight/active-users/panel")
                        .param("panel", "unknown-panel")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    private Panel panel(String key, String metric, String label, PanelBucket bucket, String selectedRange) {
        return new Panel(
                key,
                metric,
                label,
                label + " 설명",
                bucket,
                selectedRange,
                false,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 21),
                List.of(new RangeOption(selectedRange, "기본")),
                List.of(new Point(LocalDate.of(2026, 10, 21), 120d)),
                false
        );
    }
}
