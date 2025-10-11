package apu.saerok_admin.web;

import apu.saerok_admin.infra.audit.AdminAuditLogClient;
import apu.saerok_admin.infra.audit.dto.AdminAuditLogListResponse;
import apu.saerok_admin.web.view.AdminAuditLogItem;
import apu.saerok_admin.web.view.Breadcrumb;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Controller
@RequestMapping("/admin/audit-logs")
public class AdminAuditLogController {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditLogController.class);
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final List<Integer> PAGE_SIZE_OPTIONS = List.of(10, 20, 50);
    private static final Map<String, ActionPresentation> ACTION_PRESENTATIONS = Map.of(
            "REPORT_IGNORED", new ActionPresentation("신고 무시", "신고를 추가 조치 없이 마감했습니다.", "text-bg-secondary"),
            "COLLECTION_DELETED", new ActionPresentation("컬렉션 삭제", "신고된 컬렉션을 삭제했습니다.", "text-bg-danger"),
            "COMMENT_DELETED", new ActionPresentation("댓글 삭제", "신고된 댓글을 삭제했습니다.", "text-bg-danger")
    );
    private static final Map<String, String> TARGET_LABELS = Map.of(
            "REPORT_COLLECTION", "컬렉션 신고",
            "REPORT_COMMENT", "댓글 신고",
            "COLLECTION", "컬렉션",
            "COMMENT", "댓글"
    );
    private static final String UNKNOWN_ACTION_LABEL = "기록되지 않은 작업";
    private static final String UNKNOWN_ACTION_DESCRIPTION = "정의되지 않은 관리자 활동입니다.";
    private static final String UNKNOWN_ACTION_BADGE = "text-bg-secondary";
    private static final String UNKNOWN_TARGET_LABEL = "알 수 없는 대상";
    private static final ObjectMapper METADATA_MAPPER = new ObjectMapper();

    private final AdminAuditLogClient adminAuditLogClient;

    public AdminAuditLogController(AdminAuditLogClient adminAuditLogClient) {
        this.adminAuditLogClient = adminAuditLogClient;
    }

    @GetMapping
    public String list(@RequestParam(name = "page", required = false) Integer page,
                       @RequestParam(name = "size", required = false) Integer size,
                       Model model) {
        model.addAttribute("pageTitle", "관리자 활동 로그");
        model.addAttribute("activeMenu", "adminAuditLogs");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.active("관리자 활동 로그")
        ));
        ensureToastMessages(model);

        int resolvedPage = page != null && page >= 1 ? page : 1;
        int resolvedSize = size != null && size > 0 ? size : DEFAULT_PAGE_SIZE;

        try {
            AdminAuditLogListResponse response = adminAuditLogClient.listAuditLogs(resolvedPage, resolvedSize);
            List<AdminAuditLogItem> logs = response != null && response.items() != null
                    ? response.items().stream().map(this::toViewItem).toList()
                    : List.of();

            int rangeStart = logs.isEmpty() ? 0 : (resolvedPage - 1) * resolvedSize + 1;
            int rangeEnd = (resolvedPage - 1) * resolvedSize + logs.size();

            model.addAttribute("logs", logs);
            model.addAttribute("page", resolvedPage);
            model.addAttribute("size", resolvedSize);
            model.addAttribute("hasPrevious", resolvedPage > 1);
            model.addAttribute("hasNext", logs.size() == resolvedSize);
            model.addAttribute("rangeStart", rangeStart);
            model.addAttribute("rangeEnd", rangeEnd);
            model.addAttribute("loadErrorMessage", null);
        } catch (RestClientResponseException exception) {
            log.warn("Failed to load admin audit logs. status={}, body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            attachEmptyState(model, resolvedPage, resolvedSize,
                    "관리자 활동 로그를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
        } catch (RestClientException exception) {
            log.warn("Failed to load admin audit logs.", exception);
            attachEmptyState(model, resolvedPage, resolvedSize,
                    "관리자 활동 로그를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
        }

        model.addAttribute("pageSizeOptions", PAGE_SIZE_OPTIONS);
        return "admin-audit/list";
    }

    private AdminAuditLogItem toViewItem(AdminAuditLogListResponse.Item item) {
        long id = item.id() != null ? item.id() : 0L;
        LocalDateTime occurredAt = item.createdAt();
        String adminNickname = item.admin() != null && StringUtils.hasText(item.admin().nickname())
                ? item.admin().nickname()
                : "알 수 없음";

        ActionPresentation presentation = ACTION_PRESENTATIONS.getOrDefault(
                normalizeKey(item.action()),
                new ActionPresentation(UNKNOWN_ACTION_LABEL, UNKNOWN_ACTION_DESCRIPTION, UNKNOWN_ACTION_BADGE)
        );

        String targetLabel = TARGET_LABELS.getOrDefault(normalizeKey(item.targetType()), UNKNOWN_TARGET_LABEL);
        String targetDisplay = buildTargetDisplay(targetLabel, item.targetId());
        String reportDisplay = item.reportId() != null ? "신고 #" + item.reportId() : "-";

        List<AdminAuditLogItem.MetadataEntry> metadataEntries = toMetadataEntries(item.metadata());

        return new AdminAuditLogItem(
                id,
                occurredAt,
                adminNickname,
                presentation.label(),
                presentation.description(),
                presentation.badgeClass(),
                targetDisplay,
                reportDisplay,
                metadataEntries
        );
    }

    private String buildTargetDisplay(String targetLabel, Long targetId) {
        boolean hasLabel = StringUtils.hasText(targetLabel) && !UNKNOWN_TARGET_LABEL.equals(targetLabel);
        if (targetId == null) {
            return hasLabel ? targetLabel : "-";
        }
        if (hasLabel) {
            return targetLabel + " #" + targetId;
        }
        return "#" + targetId;
    }

    private List<AdminAuditLogItem.MetadataEntry> toMetadataEntries(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return List.of();
        }

        List<AdminAuditLogItem.MetadataEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Object> entry : new LinkedHashMap<>(metadata).entrySet()) {
            String key = entry.getKey();
            String value = formatMetadataValue(entry.getValue());
            entries.add(new AdminAuditLogItem.MetadataEntry(key, value));
        }
        return List.copyOf(entries);
    }

    private String formatMetadataValue(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof Map || value instanceof List) {
            try {
                return METADATA_MAPPER.writeValueAsString(value);
            } catch (JsonProcessingException exception) {
                log.debug("Failed to serialize metadata value to JSON. value={}", value, exception);
                return value.toString();
            }
        }
        return value.toString();
    }

    private void attachEmptyState(Model model, int page, int size, String errorMessage) {
        model.addAttribute("logs", List.of());
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("hasPrevious", page > 1);
        model.addAttribute("hasNext", false);
        model.addAttribute("rangeStart", 0);
        model.addAttribute("rangeEnd", 0);
        model.addAttribute("loadErrorMessage", errorMessage);
    }

    private void ensureToastMessages(Model model) {
        if (!model.containsAttribute("toastMessages")) {
            model.addAttribute("toastMessages", List.of());
        }
    }

    private String normalizeKey(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private record ActionPresentation(String label, String description, String badgeClass) {
    }
}
