package apu.saerok_admin.web;

import apu.saerok_admin.infra.announcement.AdminAnnouncementClient;
import apu.saerok_admin.infra.announcement.dto.AdminAnnouncementDetailResponse;
import apu.saerok_admin.infra.announcement.dto.AdminAnnouncementImagePresignRequest;
import apu.saerok_admin.infra.announcement.dto.AdminAnnouncementImageRequest;
import apu.saerok_admin.infra.announcement.dto.AdminAnnouncementListResponse;
import apu.saerok_admin.infra.announcement.dto.AdminCreateAnnouncementRequest;
import apu.saerok_admin.infra.announcement.dto.AdminUpdateAnnouncementRequest;
import apu.saerok_admin.infra.announcement.dto.AnnouncementImagePresignResponse;
import apu.saerok_admin.web.view.Breadcrumb;
import apu.saerok_admin.web.view.CurrentAdminProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/notices")
public class NoticeController {

    private static final String PERMISSION_ADMIN_ANNOUNCEMENT_READ = "ADMIN_ANNOUNCEMENT_READ";
    private static final String PERMISSION_ADMIN_ANNOUNCEMENT_WRITE = "ADMIN_ANNOUNCEMENT_WRITE";

    private static final DateTimeFormatter DATETIME_LOCAL_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");

    private final AdminAnnouncementClient adminAnnouncementClient;
    private final ObjectMapper objectMapper;

    @GetMapping
    public String index(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                        Model model) {
        model.addAttribute("pageTitle", "공지사항 관리");
        model.addAttribute("activeMenu", "notices");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.active("공지사항 관리")
        ));
        model.addAttribute("toastMessages", List.of());

        boolean canRead = currentAdminProfile != null && currentAdminProfile.hasPermission(PERMISSION_ADMIN_ANNOUNCEMENT_READ);
        boolean canWrite = currentAdminProfile != null && currentAdminProfile.hasPermission(PERMISSION_ADMIN_ANNOUNCEMENT_WRITE);
        model.addAttribute("canWrite", canWrite);

        if (!canRead) {
            model.addAttribute("loadError", "공지사항을 조회할 권한이 없습니다.");
            model.addAttribute("announcements", List.of());
            return "notices/index";
        }

        try {
            AdminAnnouncementListResponse response = adminAnnouncementClient.listAnnouncements();
            List<AdminAnnouncementListResponse.Item> items = response != null && response.announcements() != null
                    ? response.announcements()
                    : List.of();
            model.addAttribute("announcements", items);
            model.addAttribute("loadError", null);
        } catch (RestClientResponseException exception) {
            log.warn("Failed to load announcements. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            model.addAttribute("loadError", "공지사항 목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
            model.addAttribute("announcements", List.of());
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load announcements.", exception);
            model.addAttribute("loadError", "공지사항 목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
            model.addAttribute("announcements", List.of());
        }

        return "notices/index";
    }

    @GetMapping("/{id:\\d+}")
    public String detail(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                         @PathVariable Long id,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        boolean canRead = currentAdminProfile != null && currentAdminProfile.hasPermission(PERMISSION_ADMIN_ANNOUNCEMENT_READ);
        boolean canWrite = currentAdminProfile != null && currentAdminProfile.hasPermission(PERMISSION_ADMIN_ANNOUNCEMENT_WRITE);

        if (!canRead) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "공지사항을 조회할 권한이 없습니다.");
            return "redirect:/notices";
        }

        try {
            AdminAnnouncementDetailResponse response = adminAnnouncementClient.getAnnouncement(id);
            if (response == null) {
                throw new IllegalStateException("Empty announcement detail response");
            }

            model.addAttribute("pageTitle", "공지사항 상세");
            model.addAttribute("activeMenu", "notices");
            model.addAttribute("breadcrumbs", List.of(
                    Breadcrumb.of("대시보드", "/"),
                    Breadcrumb.of("공지사항 관리", "/notices"),
                    Breadcrumb.active("상세")
            ));
            model.addAttribute("toastMessages", List.of());
            model.addAttribute("detail", response);
            model.addAttribute("canWrite", canWrite);
            model.addAttribute("canEdit", canWrite && !"PUBLISHED".equalsIgnoreCase(response.status()));
            return "notices/detail";
        } catch (RestClientResponseException exception) {
            log.warn("Failed to load announcement detail. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "공지사항을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
            return "redirect:/notices";
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load announcement detail.", exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "공지사항을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
            return "redirect:/notices";
        }
    }

    @GetMapping("/new")
    public String createForm(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (currentAdminProfile == null || !currentAdminProfile.hasPermission(PERMISSION_ADMIN_ANNOUNCEMENT_WRITE)) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "공지사항을 작성할 권한이 없습니다.");
            return "redirect:/notices";
        }

        model.addAttribute("pageTitle", "공지사항 작성");
        model.addAttribute("activeMenu", "notices");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.of("공지사항 관리", "/notices"),
                Breadcrumb.active("작성")
        ));
        model.addAttribute("toastMessages", List.of());
        model.addAttribute("isEdit", false);
        model.addAttribute("form", NoticeForm.empty());
        model.addAttribute("initialImagesJson", "[]");
        return "notices/compose";
    }

    @GetMapping("/edit")
    public String editForm(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                           @RequestParam Long id,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (currentAdminProfile == null || !currentAdminProfile.hasPermission(PERMISSION_ADMIN_ANNOUNCEMENT_WRITE)) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "공지사항을 수정할 권한이 없습니다.");
            return "redirect:/notices";
        }

        try {
            AdminAnnouncementDetailResponse response = adminAnnouncementClient.getAnnouncement(id);
            if (response == null) {
                throw new IllegalStateException("Empty announcement detail response");
            }

            if ("PUBLISHED".equalsIgnoreCase(response.status())) {
                redirectAttributes.addFlashAttribute("flashStatus", "error");
                redirectAttributes.addFlashAttribute("flashMessage", "이미 게시된 공지사항은 수정할 수 없습니다.");
                return "redirect:/notices";
            }

            String scheduledAt = formatForDatetimeLocal(response.scheduledAt());
            NoticeForm form = new NoticeForm(
                    response.id(),
                    nullToEmpty(response.title()),
                    nullToEmpty(response.content()),
                    Boolean.TRUE.equals(response.sendNotification()),
                    nullToEmpty(response.pushTitle()),
                    nullToEmpty(response.pushBody()),
                    nullToEmpty(response.inAppBody()),
                    scheduledAt
            );

            model.addAttribute("pageTitle", "공지사항 수정");
            model.addAttribute("activeMenu", "notices");
            model.addAttribute("breadcrumbs", List.of(
                    Breadcrumb.of("대시보드", "/"),
                    Breadcrumb.of("공지사항 관리", "/notices"),
                    Breadcrumb.active("수정")
            ));
            model.addAttribute("toastMessages", List.of());
            model.addAttribute("isEdit", true);
            model.addAttribute("form", form);
            model.addAttribute("initialImagesJson", toImagesJson(response.images()));
            return "notices/compose";
        } catch (RestClientResponseException exception) {
            log.warn("Failed to load announcement detail. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "공지사항을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
            return "redirect:/notices";
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load announcement detail.", exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "공지사항을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
            return "redirect:/notices";
        }
    }

    @PostMapping("/create")
    public String createAnnouncement(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                                     @RequestParam String title,
                                     @RequestParam(name = "contentHtml") String contentHtml,
                                     @RequestParam(name = "publishNow", defaultValue = "false") boolean publishNow,
                                     @RequestParam(name = "scheduledAt", required = false) String scheduledAtRaw,
                                     @RequestParam(name = "sendNotification", defaultValue = "false") boolean sendNotification,
                                     @RequestParam(name = "pushTitle", required = false) String pushTitle,
                                     @RequestParam(name = "pushBody", required = false) String pushBody,
                                     @RequestParam(name = "inAppBody", required = false) String inAppBody,
                                     @RequestParam(name = "imagesJson", required = false) String imagesJson,
                                     RedirectAttributes redirectAttributes) {
        if (currentAdminProfile == null || !currentAdminProfile.hasPermission(PERMISSION_ADMIN_ANNOUNCEMENT_WRITE)) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "공지사항을 작성할 권한이 없습니다.");
            return "redirect:/notices";
        }

        if (!StringUtils.hasText(title) || !StringUtils.hasText(contentHtml)) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "필수 입력값을 모두 채워주세요.");
            return "redirect:/notices/new";
        }

        LocalDateTime scheduledAt = parseDatetimeLocal(scheduledAtRaw);
        if (!publishNow && scheduledAt == null) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "예약 게시 시각을 입력해 주세요.");
            return "redirect:/notices/new";
        }

        if (sendNotification && (!StringUtils.hasText(pushTitle) || !StringUtils.hasText(pushBody) || !StringUtils.hasText(inAppBody))) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "알림을 보낼 경우 푸시 제목/본문과 인앱 알림 본문을 모두 입력해 주세요.");
            return "redirect:/notices/new";
        }

        List<AdminAnnouncementImageRequest> images = parseImages(imagesJson);

        try {
            adminAnnouncementClient.createAnnouncement(new AdminCreateAnnouncementRequest(
                    title.trim(),
                    contentHtml,
                    scheduledAt,
                    publishNow,
                    sendNotification,
                    trimToNull(pushTitle),
                    trimToNull(pushBody),
                    trimToNull(inAppBody),
                    images
            ));
            redirectAttributes.addFlashAttribute("flashStatus", "success");
            redirectAttributes.addFlashAttribute("flashMessage", publishNow ? "공지사항이 게시되었습니다." : "공지사항이 예약되었습니다.");
            return "redirect:/notices";
        } catch (RestClientResponseException exception) {
            log.warn("Failed to create announcement. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "공지사항 저장에 실패했습니다. 입력값을 확인해 주세요.");
            return "redirect:/notices/new";
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to create announcement.", exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "공지사항 저장에 실패했습니다. 잠시 후 다시 시도해주세요.");
            return "redirect:/notices/new";
        }
    }

    @PostMapping("/edit")
    public String updateAnnouncement(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                                     @RequestParam Long id,
                                     @RequestParam String title,
                                     @RequestParam(name = "contentHtml") String contentHtml,
                                     @RequestParam(name = "publishNow", defaultValue = "false") boolean publishNow,
                                     @RequestParam(name = "scheduledAt", required = false) String scheduledAtRaw,
                                     @RequestParam(name = "sendNotification", defaultValue = "false") boolean sendNotification,
                                     @RequestParam(name = "pushTitle", required = false) String pushTitle,
                                     @RequestParam(name = "pushBody", required = false) String pushBody,
                                     @RequestParam(name = "inAppBody", required = false) String inAppBody,
                                     @RequestParam(name = "imagesJson", required = false) String imagesJson,
                                     RedirectAttributes redirectAttributes) {
        if (currentAdminProfile == null || !currentAdminProfile.hasPermission(PERMISSION_ADMIN_ANNOUNCEMENT_WRITE)) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "공지사항을 수정할 권한이 없습니다.");
            return "redirect:/notices";
        }

        if (!StringUtils.hasText(title) || !StringUtils.hasText(contentHtml)) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "필수 입력값을 모두 채워주세요.");
            return "redirect:/notices/edit?id=" + id;
        }

        LocalDateTime scheduledAt = parseDatetimeLocal(scheduledAtRaw);
        if (!publishNow && scheduledAt == null) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "예약 게시 시각을 입력해 주세요.");
            return "redirect:/notices/edit?id=" + id;
        }

        if (sendNotification && (!StringUtils.hasText(pushTitle) || !StringUtils.hasText(pushBody) || !StringUtils.hasText(inAppBody))) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "알림을 보낼 경우 푸시 제목/본문과 인앱 알림 본문을 모두 입력해 주세요.");
            return "redirect:/notices/edit?id=" + id;
        }

        List<AdminAnnouncementImageRequest> images = parseImages(imagesJson);

        try {
            adminAnnouncementClient.updateAnnouncement(id, new AdminUpdateAnnouncementRequest(
                    title.trim(),
                    contentHtml,
                    scheduledAt,
                    publishNow,
                    sendNotification,
                    trimToNull(pushTitle),
                    trimToNull(pushBody),
                    trimToNull(inAppBody),
                    images
            ));
            redirectAttributes.addFlashAttribute("flashStatus", "success");
            redirectAttributes.addFlashAttribute("flashMessage", publishNow ? "공지사항이 게시되었습니다." : "공지사항이 저장되었습니다.");
            return "redirect:/notices";
        } catch (RestClientResponseException exception) {
            log.warn("Failed to update announcement. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "공지사항 저장에 실패했습니다. 입력값을 확인해 주세요.");
            return "redirect:/notices/edit?id=" + id;
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to update announcement.", exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "공지사항 저장에 실패했습니다. 잠시 후 다시 시도해주세요.");
            return "redirect:/notices/edit?id=" + id;
        }
    }

    @PostMapping("/delete")
    public String deleteAnnouncement(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                                     @RequestParam Long id,
                                     RedirectAttributes redirectAttributes) {
        if (currentAdminProfile == null || !currentAdminProfile.hasPermission(PERMISSION_ADMIN_ANNOUNCEMENT_WRITE)) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "공지사항을 삭제할 권한이 없습니다.");
            return "redirect:/notices";
        }

        try {
            adminAnnouncementClient.deleteAnnouncement(id);
            redirectAttributes.addFlashAttribute("flashStatus", "success");
            redirectAttributes.addFlashAttribute("flashMessage", "공지사항이 삭제되었습니다.");
        } catch (RestClientResponseException exception) {
            log.warn("Failed to delete announcement. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "공지사항 삭제에 실패했습니다.");
        } catch (RestClientException exception) {
            log.warn("Failed to delete announcement.", exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "공지사항 삭제에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }

        return "redirect:/notices";
    }

    @PostMapping("/image/presign")
    @ResponseBody
    public ResponseEntity<?> presignImage(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                                          @RequestBody Map<String, String> payload) {
        if (currentAdminProfile == null || !currentAdminProfile.hasPermission(PERMISSION_ADMIN_ANNOUNCEMENT_WRITE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "이미지 업로드 권한이 없습니다."));
        }

        String contentType = payload != null ? payload.get("contentType") : null;
        if (!StringUtils.hasText(contentType)) {
            return ResponseEntity.badRequest().body(Map.of("message", "contentType이 필요합니다."));
        }

        try {
            AnnouncementImagePresignResponse presign = adminAnnouncementClient.generateImagePresignUrl(
                    new AdminAnnouncementImagePresignRequest(contentType)
            );

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("presignedUrl", presign.presignedUrl());
            result.put("objectKey", presign.objectKey());
            result.put("imageUrl", presign.imageUrl());
            return ResponseEntity.ok(result);
        } catch (RestClientResponseException exception) {
            log.warn("Failed to request announcement image presign. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", "Presigned URL 발급에 실패했습니다."));
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to request announcement image presign.", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Presigned URL 발급에 실패했습니다."));
        }
    }

    private List<AdminAnnouncementImageRequest> parseImages(String imagesJson) {
        if (!StringUtils.hasText(imagesJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(imagesJson, new TypeReference<List<AdminAnnouncementImageRequest>>() {});
        } catch (Exception exception) {
            log.warn("Failed to parse imagesJson.", exception);
            return List.of();
        }
    }

    private String toImagesJson(List<AdminAnnouncementDetailResponse.Image> images) {
        if (images == null || images.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception exception) {
            log.warn("Failed to serialize images.", exception);
            return "[]";
        }
    }

    private static LocalDateTime parseDatetimeLocal(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (Exception exception) {
            try {
                return LocalDateTime.parse(raw, DATETIME_LOCAL_FORMAT);
            } catch (Exception ignore) {
                return null;
            }
        }
    }

    private static String formatForDatetimeLocal(OffsetDateTime offsetDateTime) {
        if (offsetDateTime == null) {
            return "";
        }
        try {
            return offsetDateTime.atZoneSameInstant(KST_ZONE).toLocalDateTime().format(DATETIME_LOCAL_FORMAT);
        } catch (Exception exception) {
            return "";
        }
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public record NoticeForm(
            Long id,
            String title,
            String contentHtml,
            boolean sendNotification,
            String pushTitle,
            String pushBody,
            String inAppBody,
            String scheduledAt
    ) {
        static NoticeForm empty() {
            return new NoticeForm(null, "", "", false, "", "", "", "");
        }
    }
}
