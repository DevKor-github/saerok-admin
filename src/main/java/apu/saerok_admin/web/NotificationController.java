package apu.saerok_admin.web;

import apu.saerok_admin.infra.notification.AdminNotificationClient;
import apu.saerok_admin.infra.notification.dto.AdminSendMessageRequest;
import apu.saerok_admin.infra.user.AdminUserClient;
import apu.saerok_admin.infra.user.dto.AdminUserListResponse;
import apu.saerok_admin.web.view.Breadcrumb;
import apu.saerok_admin.web.view.CurrentAdminProfile;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {

    private static final String PERMISSION_ADMIN_ANNOUNCEMENT_WRITE = "ADMIN_ANNOUNCEMENT_WRITE";
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_BODY_LENGTH = 500;

    private final AdminNotificationClient adminNotificationClient;
    private final AdminUserClient adminUserClient;

    @GetMapping("/compose")
    public String compose(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                          Model model) {
        prepareComposeModel(currentAdminProfile, model);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", NotificationMessageForm.empty());
        }
        return "notifications/compose";
    }

    @GetMapping("/users/search")
    @ResponseBody
    public ResponseEntity<?> searchUsers(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                                         @RequestParam(name = "q", required = false) String query) {
        if (currentAdminProfile == null || !currentAdminProfile.hasPermission(PERMISSION_ADMIN_ANNOUNCEMENT_WRITE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "사용자 목록을 조회할 권한이 없습니다."));
        }

        try {
            AdminUserListResponse response = adminUserClient.listUsers(query, 1, 10);
            return ResponseEntity.ok(response);
        } catch (RestClientResponseException exception) {
            log.warn("Failed to search admin users. status={}, body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "사용자 목록을 불러오지 못했습니다."));
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to search admin users.", exception);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "사용자 목록을 불러오지 못했습니다."));
        }
    }

    @PostMapping("/send")
    public String send(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                       @RequestParam(name = "userIds", required = false) String userIdsRaw,
                       @RequestParam(name = "title", required = false) String title,
                       @RequestParam(name = "body", required = false) String body,
                       RedirectAttributes redirectAttributes) {
        NotificationMessageForm form = new NotificationMessageForm(
                nullToEmpty(userIdsRaw),
                nullToEmpty(title),
                nullToEmpty(body)
        );

        if (currentAdminProfile == null || !currentAdminProfile.hasPermission(PERMISSION_ADMIN_ANNOUNCEMENT_WRITE)) {
            return redirectWithError("대상 공지를 발송할 권한이 없습니다.", form, redirectAttributes);
        }

        String trimmedTitle = title != null ? title.trim() : "";
        String trimmedBody = body != null ? body.trim() : "";

        if (!StringUtils.hasText(trimmedTitle) || !StringUtils.hasText(trimmedBody)) {
            return redirectWithError("제목과 본문을 모두 입력해 주세요.", form, redirectAttributes);
        }
        if (trimmedTitle.length() > MAX_TITLE_LENGTH) {
            return redirectWithError("제목은 100자 이내로 입력해 주세요.", form, redirectAttributes);
        }
        if (trimmedBody.length() > MAX_BODY_LENGTH) {
            return redirectWithError("본문은 500자 이내로 입력해 주세요.", form, redirectAttributes);
        }

        List<Long> userIds;
        try {
            userIds = parseUserIds(userIdsRaw);
        } catch (IllegalArgumentException exception) {
            return redirectWithError(exception.getMessage(), form, redirectAttributes);
        }

        if (userIds.isEmpty()) {
            return redirectWithError("수신자 사용자 ID를 1개 이상 입력해 주세요.", form, redirectAttributes);
        }

        try {
            adminNotificationClient.sendMessage(new AdminSendMessageRequest(userIds, trimmedTitle, trimmedBody));
            redirectAttributes.addFlashAttribute("flashStatus", "success");
            redirectAttributes.addFlashAttribute("flashMessage",
                    userIds.size() + "명에게 대상 공지 발송 요청이 접수되었습니다.");
        } catch (RestClientResponseException exception) {
            log.warn("Failed to send admin notification. status={}, body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            return redirectWithError(resolveFailureMessage(exception), form, redirectAttributes);
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to send admin notification.", exception);
            return redirectWithError("대상 공지 발송에 실패했습니다. 잠시 후 다시 시도해주세요.", form, redirectAttributes);
        }

        return "redirect:/notifications/compose";
    }

    private void prepareComposeModel(CurrentAdminProfile currentAdminProfile, Model model) {
        model.addAttribute("pageTitle", "시스템 알림 발송");
        model.addAttribute("activeMenu", "notifications");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.active("대상 공지 발송")
        ));
        model.addAttribute("toastMessages", List.of());
        model.addAttribute("canSend", currentAdminProfile != null
                && currentAdminProfile.hasPermission(PERMISSION_ADMIN_ANNOUNCEMENT_WRITE));
    }

    private String redirectWithError(String message,
                                     NotificationMessageForm form,
                                     RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("flashStatus", "error");
        redirectAttributes.addFlashAttribute("flashMessage", message);
        redirectAttributes.addFlashAttribute("form", form);
        return "redirect:/notifications/compose";
    }

    private List<Long> parseUserIds(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }

        String[] tokens = raw.trim().split("[,\\s]+");
        Set<Long> userIds = new LinkedHashSet<>();
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }

            long userId;
            try {
                userId = Long.parseLong(token);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("사용자 ID는 숫자로만 입력해 주세요.");
            }

            if (userId <= 0) {
                throw new IllegalArgumentException("사용자 ID는 1 이상의 숫자로 입력해 주세요.");
            }
            userIds.add(userId);
        }
        return List.copyOf(userIds);
    }

    private String resolveFailureMessage(RestClientResponseException exception) {
        int statusCode = exception.getStatusCode().value();
        if (statusCode == 400) {
            return "발송 요청이 거절되었습니다. 사용자 ID와 메시지 길이를 확인해 주세요.";
        }
        if (statusCode == 403) {
            return "대상 공지를 발송할 권한이 없습니다.";
        }
        return "대상 공지 발송에 실패했습니다. 잠시 후 다시 시도해주세요.";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record NotificationMessageForm(
            String userIdsRaw,
            String title,
            String body
    ) {
        static NotificationMessageForm empty() {
            return new NotificationMessageForm("", "", "");
        }
    }
}
