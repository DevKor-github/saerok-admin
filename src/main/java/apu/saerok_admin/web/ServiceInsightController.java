package apu.saerok_admin.web;

import apu.saerok_admin.web.serviceinsight.ServiceInsightAjaxResponse;
import apu.saerok_admin.web.serviceinsight.ServiceInsightQuery;
import apu.saerok_admin.web.serviceinsight.ServiceInsightRangePreset;
import apu.saerok_admin.web.serviceinsight.ServiceInsightService;
import apu.saerok_admin.web.view.Breadcrumb;
import apu.saerok_admin.web.view.ServiceInsightViewModel;
import apu.saerok_admin.web.view.ToastMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Controller
public class ServiceInsightController {

    private static final Logger log = LoggerFactory.getLogger(ServiceInsightController.class);
    private static final String ERROR_TOAST_ID = "toastServiceInsightError";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
    private static final ServiceInsightRangePreset DEFAULT_PRESET = ServiceInsightRangePreset.LAST_14_DAYS;

    private final ServiceInsightService serviceInsightService;
    private final ObjectMapper objectMapper;

    public ServiceInsightController(ServiceInsightService serviceInsightService, ObjectMapper objectMapper) {
        this.serviceInsightService = serviceInsightService;
        // ObjectMapper 설정 강화
        this.objectMapper = objectMapper.copy()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @GetMapping(value = "/service-insight", produces = MediaType.TEXT_HTML_VALUE)
    public String serviceInsight(
            Model model,
            @RequestParam(value = "range", required = false) String rangeParam,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        model.addAttribute("pageTitle", "서비스 인사이트");
        model.addAttribute("activeMenu", "serviceInsight");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.active("서비스 인사이트")
        ));
        ensureToastMessages(model);

        PageData pageData = loadPageData(rangeParam, startDate, endDate);
        ServiceInsightViewModel viewModel = pageData.viewModel();
        RangeSelection rangeSelection = pageData.rangeSelection();
        if (pageData.hadError()) {
            attachErrorToast(model);
        }

        model.addAttribute("serviceInsight", viewModel);
        String chartDataJson = toJson(viewModel);
        model.addAttribute("chartDataJson", chartDataJson);
        model.addAttribute("rangeQuickOptions", ServiceInsightRangePreset.quickSelections());
        model.addAttribute("selectedRange", rangeSelection.preset().paramValue());
        model.addAttribute("customRangeActive", rangeSelection.preset() == ServiceInsightRangePreset.CUSTOM);
        model.addAttribute("selectedStartDate", rangeSelection.query().startDate());
        model.addAttribute("selectedEndDate", rangeSelection.query().endDate());

        log.debug("Chart data JSON length: {}", chartDataJson.length());

        return "service-insight/index";
    }

    @GetMapping(value = "/service-insight", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServiceInsightAjaxResponse> serviceInsightData(
            @RequestParam(value = "range", required = false) String rangeParam,
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        PageData pageData = loadPageData(rangeParam, startDate, endDate);
        RangeSelection rangeSelection = pageData.rangeSelection();

        ServiceInsightAjaxResponse response = new ServiceInsightAjaxResponse(
                pageData.viewModel(),
                rangeSelection.preset().paramValue(),
                rangeSelection.preset() == ServiceInsightRangePreset.CUSTOM,
                rangeSelection.query().startDate(),
                rangeSelection.query().endDate(),
                pageData.hadError()
        );

        return ResponseEntity.ok(response);
    }

    private void ensureToastMessages(Model model) {
        if (!model.containsAttribute("toastMessages")) {
            model.addAttribute("toastMessages", List.of());
        }
    }

    private PageData loadPageData(String rangeParam, LocalDate startDate, LocalDate endDate) {
        RangeSelection rangeSelection = resolveRange(rangeParam, startDate, endDate);

        try {
            ServiceInsightViewModel viewModel = serviceInsightService.loadViewModel(rangeSelection.query());
            log.info("Successfully loaded service insight view model with {} metrics (range: {} - {}, preset: {})",
                    viewModel.metricOptions().size(),
                    rangeSelection.query().startDate(),
                    rangeSelection.query().endDate(),
                    rangeSelection.preset().name());
            return new PageData(rangeSelection, viewModel, false);
        } catch (RestClientResponseException exception) {
            log.warn(
                    "Failed to load service insight stats. status={}, body={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString(),
                    exception
            );
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load service insight stats.", exception);
        }

        ServiceInsightViewModel fallback = serviceInsightService.defaultViewModel();
        return new PageData(rangeSelection, fallback, true);
    }

    private RangeSelection resolveRange(String rangeParam, LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now(DEFAULT_ZONE);

        ServiceInsightRangePreset requestedPreset = ServiceInsightRangePreset.fromParameter(rangeParam)
                .orElse(DEFAULT_PRESET);

        if (startDate != null || endDate != null) {
            requestedPreset = ServiceInsightRangePreset.CUSTOM;
        }

        if (requestedPreset == ServiceInsightRangePreset.ALL) {
            return new RangeSelection(ServiceInsightRangePreset.ALL, ServiceInsightQuery.all());
        }

        if (requestedPreset == ServiceInsightRangePreset.CUSTOM) {
            if (startDate == null || endDate == null) {
                log.debug("Incomplete custom range supplied (start={}, end={}), falling back to default preset {}",
                        startDate,
                        endDate,
                        DEFAULT_PRESET.name());
                return buildPresetSelection(DEFAULT_PRESET, today);
            }

            LocalDate effectiveStart = startDate;
            LocalDate effectiveEnd = endDate;

            if (effectiveEnd.isBefore(effectiveStart)) {
                effectiveStart = endDate;
                effectiveEnd = startDate;
            }

            if (effectiveEnd.isAfter(today)) {
                effectiveEnd = today;
            }

            if (effectiveStart.isAfter(effectiveEnd)) {
                effectiveStart = effectiveEnd;
            }

            return new RangeSelection(ServiceInsightRangePreset.CUSTOM, new ServiceInsightQuery(effectiveStart, effectiveEnd));
        }

        return buildPresetSelection(requestedPreset, today);
    }

    private RangeSelection buildPresetSelection(ServiceInsightRangePreset preset, LocalDate today) {
        return preset.toWindow(today)
                .map(window -> new RangeSelection(preset, new ServiceInsightQuery(window.startDate(), window.endDate())))
                .orElseGet(() -> new RangeSelection(ServiceInsightRangePreset.ALL, ServiceInsightQuery.all()));
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
            String json = objectMapper.writeValueAsString(viewModel);
            log.debug("Serialized view model to JSON successfully");
            return json;
        } catch (JsonProcessingException exception) {
            log.error("Failed to serialize service insight payload.", exception);
            return "{\"metricOptions\":[],\"series\":[],\"componentLabels\":{}}";
        }
    }

    private record RangeSelection(ServiceInsightRangePreset preset, ServiceInsightQuery query) {
    }

    private record PageData(RangeSelection rangeSelection, ServiceInsightViewModel viewModel, boolean hadError) {
    }
}
