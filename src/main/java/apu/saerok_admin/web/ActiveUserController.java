package apu.saerok_admin.web;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import apu.saerok_admin.web.activeusers.ActiveUserPanel;
import apu.saerok_admin.web.activeusers.ActiveUserRangeSelection;
import apu.saerok_admin.web.activeusers.ActiveUserService;
import apu.saerok_admin.web.view.ActiveUserViewModel;
import apu.saerok_admin.web.view.ActiveUserViewModel.RangeOption;
import apu.saerok_admin.web.view.Breadcrumb;
import apu.saerok_admin.web.view.ToastMessage;

/**
 * 활성 사용자수 탭. DAU·WAU·MAU를 패널별 기간으로 상시 노출한다.
 */
@Controller
public class ActiveUserController {

    private static final Logger log = LoggerFactory.getLogger(ActiveUserController.class);
    private static final String ERROR_TOAST_ID = "toastActiveUserError";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter MONTH_VALUE = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final int MONTH_OPTION_COUNT = 36;

    private final ActiveUserService activeUserService;
    private final ObjectMapper objectMapper;

    public ActiveUserController(ActiveUserService activeUserService, ObjectMapper objectMapper) {
        this.activeUserService = activeUserService;
        this.objectMapper = objectMapper.copy()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @GetMapping(value = "/service-insight/active-users", produces = MediaType.TEXT_HTML_VALUE)
    public String activeUsers(
            Model model,
            @RequestParam(value = "dauRange", required = false) String dauRange,
            @RequestParam(value = "dauStartDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dauStartDate,
            @RequestParam(value = "dauEndDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dauEndDate,
            @RequestParam(value = "wauRange", required = false) String wauRange,
            @RequestParam(value = "wauStartDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate wauStartDate,
            @RequestParam(value = "wauEndDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate wauEndDate,
            @RequestParam(value = "mauRange", required = false) String mauRange,
            @RequestParam(value = "mauStartDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate mauStartDate,
            @RequestParam(value = "mauEndDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate mauEndDate
    ) {
        model.addAttribute("pageTitle", "서비스 인사이트 · 활성 사용자수");
        model.addAttribute("activeMenu", "serviceInsightActiveUsers");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.of("서비스 인사이트", "/service-insight/time-series"),
                Breadcrumb.active("활성 사용자수")
        ));
        ensureToastMessages(model);

        LocalDate today = LocalDate.now(DEFAULT_ZONE);
        List<ActiveUserRangeSelection> selections = List.of(
                ActiveUserRangeSelection.resolve(ActiveUserPanel.DAU, dauRange, dauStartDate, dauEndDate, today),
                ActiveUserRangeSelection.resolve(ActiveUserPanel.WAU, wauRange, wauStartDate, wauEndDate, today),
                ActiveUserRangeSelection.resolve(ActiveUserPanel.MAU, mauRange, mauStartDate, mauEndDate, today)
        );

        ActiveUserViewModel viewModel = activeUserService.load(selections);
        boolean hadError = viewModel.panels().stream().anyMatch(ActiveUserViewModel.Panel::hadError);
        if (hadError) {
            attachErrorToast(model);
        }

        model.addAttribute("activeUsers", viewModel);
        model.addAttribute("activeUsersJson", toJson(viewModel));
        model.addAttribute("monthOptions", monthOptions(today));

        return "service-insight/active-users";
    }

    @GetMapping(value = "/service-insight/active-users/panel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ActiveUserViewModel.Panel> activeUserPanel(
            @RequestParam("panel") String panelKey,
            @RequestParam(value = "range", required = false) String rangeParam,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ActiveUserPanel.fromKey(panelKey)
                .map(panel -> {
                    LocalDate today = LocalDate.now(DEFAULT_ZONE);
                    ActiveUserRangeSelection selection =
                            ActiveUserRangeSelection.resolve(panel, rangeParam, startDate, endDate, today);
                    return ResponseEntity.ok(activeUserService.loadPanel(selection));
                })
                .orElseGet(() -> {
                    log.debug("Unknown active user panel requested: {}", panelKey);
                    return ResponseEntity.badRequest().build();
                });
    }

    /**
     * MAU 패널의 직접 설정은 월 단위로 고르게 하려고 월 목록을 미리 내려준다. (최신 월이 먼저)
     */
    private List<RangeOption> monthOptions(LocalDate today) {
        List<RangeOption> options = new ArrayList<>();
        LocalDate cursor = today.withDayOfMonth(1);
        for (int index = 0; index < MONTH_OPTION_COUNT; index++) {
            LocalDate month = cursor.minusMonths(index);
            options.add(new RangeOption(
                    MONTH_VALUE.format(month),
                    month.getYear() + "년 " + month.getMonthValue() + "월"
            ));
        }
        return List.copyOf(options);
    }

    private void ensureToastMessages(Model model) {
        if (!model.containsAttribute("toastMessages")) {
            model.addAttribute("toastMessages", List.of());
        }
    }

    private void attachErrorToast(Model model) {
        ToastMessage errorToast = new ToastMessage(
                ERROR_TOAST_ID,
                "데이터 로드 실패",
                "활성 사용자 통계를 일부 불러오지 못했습니다. 잠시 후 다시 시도해주세요.",
                "danger",
                false
        );
        List<ToastMessage> messages = new ArrayList<>();
        Object existing = model.getAttribute("toastMessages");
        if (existing instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof ToastMessage toastMessage && !ERROR_TOAST_ID.equals(toastMessage.id())) {
                    messages.add(toastMessage);
                }
            }
        }
        messages.add(errorToast);
        model.addAttribute("toastMessages", List.copyOf(messages));
    }

    private String toJson(ActiveUserViewModel viewModel) {
        try {
            return objectMapper.writeValueAsString(viewModel);
        } catch (JsonProcessingException exception) {
            log.error("Failed to serialize active user payload.", exception);
            return "{\"panels\":[]}";
        }
    }
}
