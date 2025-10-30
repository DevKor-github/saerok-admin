package apu.saerok_admin.web;

import apu.saerok_admin.web.serviceinsight.ServiceInsightService;
import apu.saerok_admin.web.view.Breadcrumb;
import apu.saerok_admin.web.view.ServiceInsightViewModel;
import apu.saerok_admin.web.view.ToastMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Controller
public class ServiceInsightController {

    private static final Logger log = LoggerFactory.getLogger(ServiceInsightController.class);
    private static final String ERROR_TOAST_ID = "toastServiceInsightError";

    private final ServiceInsightService serviceInsightService;
    private final ObjectMapper objectMapper;

    public ServiceInsightController(ServiceInsightService serviceInsightService, ObjectMapper objectMapper) {
        this.serviceInsightService = serviceInsightService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/service-insight")
    public String serviceInsight(Model model) {
        model.addAttribute("pageTitle", "서비스 인사이트");
        model.addAttribute("activeMenu", "serviceInsight");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.active("서비스 인사이트")
        ));
        ensureToastMessages(model);

        ServiceInsightViewModel viewModel;
        try {
            viewModel = serviceInsightService.loadViewModel();
        } catch (RestClientResponseException exception) {
            log.warn(
                    "Failed to load service insight stats. status={}, body={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString(),
                    exception
            );
            viewModel = serviceInsightService.defaultViewModel();
            attachErrorToast(model);
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load service insight stats.", exception);
            viewModel = serviceInsightService.defaultViewModel();
            attachErrorToast(model);
        }

        model.addAttribute("serviceInsight", viewModel);
        model.addAttribute("chartDataJson", toJson(viewModel));
        return "service-insight/index";
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
                "통계 데이터를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.",
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

    private String toJson(ServiceInsightViewModel viewModel) {
        try {
            return objectMapper.writeValueAsString(viewModel);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to serialize service insight payload.", exception);
            return "{\"metricOptions\":[],\"series\":[],\"componentLabels\":{}}";
        }
    }
}
