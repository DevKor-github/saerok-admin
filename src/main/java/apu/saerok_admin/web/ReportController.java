package apu.saerok_admin.web;

import apu.saerok_admin.infra.report.AdminReportClient;
import apu.saerok_admin.infra.report.dto.CollectionCommentsResponse;
import apu.saerok_admin.infra.report.dto.CollectionDetailResponse;
import apu.saerok_admin.infra.report.dto.ReportedCollectionDetailResponse;
import apu.saerok_admin.infra.report.dto.ReportedCollectionListResponse;
import apu.saerok_admin.infra.report.dto.ReportedCommentDetailResponse;
import apu.saerok_admin.infra.report.dto.ReportedCommentListResponse;
import apu.saerok_admin.web.view.Breadcrumb;
import apu.saerok_admin.web.view.ReportDetail;
import apu.saerok_admin.web.view.ReportListItem;
import apu.saerok_admin.web.view.ReportType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/reports")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);
    private static final Map<String, ReportType> TYPE_PARAM_MAP = Map.of(
            "collection", ReportType.COLLECTION,
            "collections", ReportType.COLLECTION,
            "comment", ReportType.COMMENT,
            "comments", ReportType.COMMENT
    );

    private final AdminReportClient adminReportClient;

    public ReportController(AdminReportClient adminReportClient) {
        this.adminReportClient = adminReportClient;
    }

    @GetMapping
    public String list(@RequestParam(name = "type", required = false) List<String> types,
                       Model model) {
        model.addAttribute("pageTitle", "신고 관리");
        model.addAttribute("activeMenu", "reports");
        model.addAttribute("breadcrumbs", List.of(Breadcrumb.of("대시보드", "/"), Breadcrumb.active("신고")));
        ensureToastMessages(model);

        List<String> normalizedTypes = normalizeTypeParams(types);
        Set<ReportType> selectedTypes = toReportTypes(normalizedTypes);
        boolean includeCollections = selectedTypes.isEmpty() || selectedTypes.contains(ReportType.COLLECTION);
        boolean includeComments = selectedTypes.isEmpty() || selectedTypes.contains(ReportType.COMMENT);
        model.addAttribute("collectionFilterActive", includeCollections);
        model.addAttribute("commentFilterActive", includeComments);
        model.addAttribute("selectedTypeParams", normalizedTypes);

        try {
            List<ReportedCollectionListResponse.Item> rawCollectionReports = Optional.ofNullable(adminReportClient.listCollectionReports())
                    .map(ReportedCollectionListResponse::items)
                    .orElseGet(List::of);
            List<ReportedCommentListResponse.Item> rawCommentReports = Optional.ofNullable(adminReportClient.listCommentReports())
                    .map(ReportedCommentListResponse::items)
                    .orElseGet(List::of);

            Map<Long, String> collectionNotes = includeCollections ? loadCollectionNotes(rawCollectionReports) : Map.of();

            List<ReportListItem> collectionReports = rawCollectionReports.stream()
                    .map(item -> toCollectionListItem(item, collectionNotes.get(item.reportId())))
                    .toList();
            List<ReportListItem> commentReports = rawCommentReports.stream()
                    .map(this::toCommentListItem)
                    .toList();

            List<ReportListItem> combined = Stream.concat(collectionReports.stream(), commentReports.stream())
                    .sorted(Comparator.comparing(ReportListItem::reportedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .collect(Collectors.toCollection(ArrayList::new));

            List<ReportListItem> filtered = combined;
            if (!selectedTypes.isEmpty()) {
                filtered = combined.stream()
                        .filter(item -> selectedTypes.contains(item.type()))
                        .toList();
            }

            model.addAttribute("reports", filtered);
            model.addAttribute("totalCount", combined.size());
            model.addAttribute("filteredCount", filtered.size());
            model.addAttribute("collectionCount", collectionReports.size());
            model.addAttribute("commentCount", commentReports.size());
            model.addAttribute("loadErrorMessage", null);
        } catch (RestClientResponseException exception) {
            log.warn("Failed to load reports from backend. status={}, body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            model.addAttribute("reports", List.of());
            model.addAttribute("loadErrorMessage", "신고 목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load reports from backend.", exception);
            model.addAttribute("reports", List.of());
            model.addAttribute("loadErrorMessage", "신고 목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
        }

        return "reports/list";
    }

    @GetMapping("/collections/{reportId}")
    public String collectionDetail(@PathVariable long reportId,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        model.addAttribute("pageTitle", "신고 상세");
        model.addAttribute("activeMenu", "reports");
        ensureToastMessages(model);

        try {
            ReportedCollectionDetailResponse detailResponse = adminReportClient.getCollectionReportDetail(reportId);
            ReportedCollectionListResponse.Item metadata = findCollectionReportMetadata(reportId);

            ReportDetail detail = buildCollectionDetail(detailResponse, metadata);
            model.addAttribute("detail", detail);
            model.addAttribute("breadcrumbs", List.of(
                    Breadcrumb.of("대시보드", "/"),
                    Breadcrumb.of("신고", "/reports"),
                    Breadcrumb.active("새록 신고 #" + reportId)
            ));
            attachFlashDefaults(model);
            return "reports/detail";
        } catch (RestClientResponseException exception) {
            log.warn("Failed to load collection report detail. status={}, body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "신고 #" + reportId + " 정보를 불러오지 못했습니다.");
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load collection report detail.", exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "신고 #" + reportId + " 정보를 불러오지 못했습니다.");
        }

        return "redirect:/reports";
    }

    @GetMapping("/comments/{reportId}")
    public String commentDetail(@PathVariable long reportId,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        model.addAttribute("pageTitle", "신고 상세");
        model.addAttribute("activeMenu", "reports");
        ensureToastMessages(model);

        try {
            ReportedCommentDetailResponse detailResponse = adminReportClient.getCommentReportDetail(reportId);
            ReportedCommentListResponse.Item metadata = findCommentReportMetadata(reportId);

            ReportDetail detail = buildCommentDetail(detailResponse, metadata);
            model.addAttribute("detail", detail);
            model.addAttribute("breadcrumbs", List.of(
                    Breadcrumb.of("대시보드", "/"),
                    Breadcrumb.of("신고", "/reports"),
                    Breadcrumb.active("댓글 신고 #" + reportId)
            ));
            attachFlashDefaults(model);
            return "reports/detail";
        } catch (RestClientResponseException exception) {
            log.warn("Failed to load comment report detail. status={}, body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "신고 #" + reportId + " 정보를 불러오지 못했습니다.");
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load comment report detail.", exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "신고 #" + reportId + " 정보를 불러오지 못했습니다.");
        }

        return "redirect:/reports";
    }

    @PostMapping("/collections/{reportId}/ignore")
    public String ignoreCollection(@PathVariable long reportId,
                                   @RequestParam(name = "redirect", defaultValue = "list") String redirect,
                                   @RequestParam(name = "returnType", required = false) List<String> returnTypes,
                                   RedirectAttributes redirectAttributes) {
        return performAction(() -> adminReportClient.ignoreCollectionReport(reportId),
                reportId,
                "신고 #" + reportId + "을(를) 무시 처리했습니다.",
                "신고 #" + reportId + " 무시 처리에 실패했습니다.",
                redirect,
                returnTypes,
                "/reports/collections/" + reportId,
                redirectAttributes);
    }

    @PostMapping("/collections/{reportId}/delete")
    public String deleteCollectionByReport(@PathVariable long reportId,
                                           @RequestParam(name = "redirect", defaultValue = "list") String redirect,
                                           @RequestParam(name = "returnType", required = false) List<String> returnTypes,
                                           RedirectAttributes redirectAttributes) {
        return performAction(() -> adminReportClient.deleteCollectionByReport(reportId),
                reportId,
                "신고 대상 새록을 삭제했습니다.",
                "신고 대상 새록 삭제에 실패했습니다.",
                redirect,
                returnTypes,
                "/reports/collections/" + reportId,
                redirectAttributes);
    }

    @PostMapping("/comments/{reportId}/ignore")
    public String ignoreComment(@PathVariable long reportId,
                                @RequestParam(name = "redirect", defaultValue = "list") String redirect,
                                @RequestParam(name = "returnType", required = false) List<String> returnTypes,
                                RedirectAttributes redirectAttributes) {
        return performAction(() -> adminReportClient.ignoreCommentReport(reportId),
                reportId,
                "신고 #" + reportId + "을(를) 무시 처리했습니다.",
                "신고 #" + reportId + " 무시 처리에 실패했습니다.",
                redirect,
                returnTypes,
                "/reports/comments/" + reportId,
                redirectAttributes);
    }

    @PostMapping("/comments/{reportId}/delete")
    public String deleteCommentByReport(@PathVariable long reportId,
                                        @RequestParam(name = "redirect", defaultValue = "list") String redirect,
                                        @RequestParam(name = "returnType", required = false) List<String> returnTypes,
                                        RedirectAttributes redirectAttributes) {
        return performAction(() -> adminReportClient.deleteCommentByReport(reportId),
                reportId,
                "신고 대상 댓글을 삭제했습니다.",
                "신고 대상 댓글 삭제에 실패했습니다.",
                redirect,
                returnTypes,
                "/reports/comments/" + reportId,
                redirectAttributes);
    }

    private String performAction(Runnable action,
                                 long reportId,
                                 String successMessage,
                                 String failureMessage,
                                 String redirect,
                                 List<String> returnTypes,
                                 String detailPath,
                                 RedirectAttributes redirectAttributes) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("flashStatus", "success");
            redirectAttributes.addFlashAttribute("flashMessage", successMessage);
        } catch (RestClientResponseException exception) {
            log.warn("Failed to process report action. status={}, body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", failureMessage);
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to process report action.", exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", failureMessage);
        }

        return determineRedirectUrl(redirect, returnTypes, detailPath);
    }

    private String determineRedirectUrl(String redirect,
                                        List<String> returnTypes,
                                        String detailPath) {
        if ("detail".equalsIgnoreCase(redirect)) {
            return "redirect:" + detailPath;
        }

        StringBuilder url = new StringBuilder("redirect:/reports");
        List<String> normalized = normalizeTypeParams(returnTypes);
        if (!normalized.isEmpty() && normalized.size() < ReportType.values().length) {
            String separator = "?";
            for (String value : normalized) {
                url.append(separator).append("type=").append(value);
                separator = "&";
            }
        }
        return url.toString();
    }

    private List<String> normalizeTypeParams(List<String> types) {
        if (types == null || types.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String type : types) {
            if (!StringUtils.hasText(type)) {
                continue;
            }
            String lowerCase = type.toLowerCase(Locale.ROOT);
            if ("all".equals(lowerCase)) {
                return List.of();
            }
            ReportType reportType = TYPE_PARAM_MAP.get(lowerCase);
            if (reportType != null) {
                normalized.add(canonicalParam(reportType));
            }
        }
        return List.copyOf(normalized);
    }

    private Set<ReportType> toReportTypes(List<String> normalized) {
        if (normalized.isEmpty()) {
            return EnumSet.noneOf(ReportType.class);
        }
        EnumSet<ReportType> result = EnumSet.noneOf(ReportType.class);
        for (String value : normalized) {
            ReportType type = TYPE_PARAM_MAP.get(value);
            if (type != null) {
                result.add(type);
            }
        }
        return result;
    }

    private String canonicalParam(ReportType type) {
        return switch (type) {
            case COLLECTION -> "collection";
            case COMMENT -> "comment";
        };
    }

    private ReportListItem toCollectionListItem(ReportedCollectionListResponse.Item item, String note) {
        String detailPath = "/reports/collections/" + item.reportId();
        String preview = abbreviate(note, 80);
        return new ReportListItem(
                item.reportId(),
                ReportType.COLLECTION,
                item.reportedAt(),
                "새록 #" + item.collectionId(),
                preview,
                item.reporter() != null ? item.reporter().nickname() : "-",
                item.reportedUser() != null ? item.reportedUser().nickname() : "-",
                detailPath,
                detailPath + "/ignore",
                detailPath + "/delete"
        );
    }

    private ReportListItem toCommentListItem(ReportedCommentListResponse.Item item) {
        String detailPath = "/reports/comments/" + item.reportId();
        String preview = abbreviate(item.contentPreview(), 80);
        return new ReportListItem(
                item.reportId(),
                ReportType.COMMENT,
                item.reportedAt(),
                "댓글 #" + item.commentId() + " (새록 #" + item.collectionId() + ")",
                preview,
                item.reporter() != null ? item.reporter().nickname() : "-",
                item.reportedUser() != null ? item.reportedUser().nickname() : "-",
                detailPath,
                detailPath + "/ignore",
                detailPath + "/delete"
        );
    }

    private Map<Long, String> loadCollectionNotes(List<ReportedCollectionListResponse.Item> reports) {
        Map<Long, String> notes = new HashMap<>();
        for (ReportedCollectionListResponse.Item item : reports) {
            if (item == null || item.reportId() == null) {
                continue;
            }
            try {
                ReportedCollectionDetailResponse detail = adminReportClient.getCollectionReportDetail(item.reportId());
                if (detail != null && detail.collection() != null) {
                    notes.put(item.reportId(), detail.collection().note());
                }
            } catch (RestClientResponseException ex) {
                log.warn("Failed to fetch collection note for report {}. status={}, body={}",
                        item.reportId(), ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            } catch (RestClientException | IllegalStateException ex) {
                log.warn("Failed to fetch collection note for report {}.", item.reportId(), ex);
            }
        }
        return notes;
    }

    private ReportDetail buildCollectionDetail(ReportedCollectionDetailResponse detailResponse,
                                               ReportedCollectionListResponse.Item metadata) {
        ReportDetail.Person reporter = metadata != null && metadata.reporter() != null
                ? new ReportDetail.Person(metadata.reporter().userId(), metadata.reporter().nickname())
                : null;
        ReportDetail.Person reportedUser = metadata != null && metadata.reportedUser() != null
                ? new ReportDetail.Person(metadata.reportedUser().userId(), metadata.reportedUser().nickname())
                : null;
        List<ReportDetail.Comment> comments = mapComments(detailResponse.comments());

        return new ReportDetail(
                detailResponse.reportId(),
                ReportType.COLLECTION,
                metadata != null ? metadata.reportedAt() : null,
                reporter,
                reportedUser,
                mapCollection(detailResponse.collection()),
                comments,
                null,
                "/reports/collections/" + detailResponse.reportId() + "/ignore",
                "/reports/collections/" + detailResponse.reportId() + "/delete"
        );
    }

    private ReportDetail buildCommentDetail(ReportedCommentDetailResponse detailResponse,
                                            ReportedCommentListResponse.Item metadata) {
        ReportDetail.Person reporter = metadata != null && metadata.reporter() != null
                ? new ReportDetail.Person(metadata.reporter().userId(), metadata.reporter().nickname())
                : null;
        ReportDetail.Person reportedUser = metadata != null && metadata.reportedUser() != null
                ? new ReportDetail.Person(metadata.reportedUser().userId(), metadata.reportedUser().nickname())
                : null;
        List<ReportDetail.Comment> comments = mapComments(detailResponse.comments());
        ReportedCommentDetailResponse.ReportedComment comment = detailResponse.comment();
        ReportDetail.ReportedComment reportedComment = comment != null
                ? new ReportDetail.ReportedComment(
                comment.commentId(),
                comment.userId(),
                comment.nickname(),
                comment.content(),
                comment.createdAt(),
                comment.updatedAt())
                : null;

        LocalDateTime reportedAt = metadata != null ? metadata.reportedAt() : null;

        return new ReportDetail(
                detailResponse.reportId(),
                ReportType.COMMENT,
                reportedAt,
                reporter,
                reportedUser,
                mapCollection(detailResponse.collection()),
                comments,
                reportedComment,
                "/reports/comments/" + detailResponse.reportId() + "/ignore",
                "/reports/comments/" + detailResponse.reportId() + "/delete"
        );
    }

    private ReportDetail.Collection mapCollection(CollectionDetailResponse collection) {
        if (collection == null) {
            return null;
        }
        String accessLevel = collection.accessLevel();
        String accessLevelLabel = toAccessLevelLabel(accessLevel);
        CollectionDetailResponse.BirdInfo bird = collection.bird();
        CollectionDetailResponse.UserInfo user = collection.user();
        return new ReportDetail.Collection(
                collection.collectionId(),
                bird != null ? bird.birdId() : null,
                bird != null ? bird.koreanName() : null,
                bird != null ? bird.scientificName() : null,
                collection.discoveredDate(),
                collection.latitude(),
                collection.longitude(),
                collection.locationAlias(),
                collection.address(),
                collection.note(),
                accessLevel,
                accessLevelLabel,
                collection.likeCount(),
                collection.commentCount(),
                collection.isLiked(),
                collection.isMine(),
                user != null ? user.userId() : null,
                user != null ? user.nickname() : null,
                user != null ? user.profileImageUrl() : null,
                collection.imageUrl()
        );
    }

    private List<ReportDetail.Comment> mapComments(CollectionCommentsResponse commentsResponse) {
        if (commentsResponse == null || commentsResponse.items() == null) {
            return List.of();
        }
        return commentsResponse.items().stream()
                .map(item -> new ReportDetail.Comment(
                        item.commentId(),
                        item.userId(),
                        item.nickname(),
                        item.content(),
                        item.likeCount(),
                        item.createdAt(),
                        item.updatedAt()
                ))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private ReportedCollectionListResponse.Item findCollectionReportMetadata(long reportId) {
        return Optional.of(adminReportClient.listCollectionReports())
                .map(ReportedCollectionListResponse::items)
                .orElse(List.of())
                .stream()
                .filter(item -> item.reportId() != null && item.reportId() == reportId)
                .findFirst()
                .orElse(null);
    }

    private ReportedCommentListResponse.Item findCommentReportMetadata(long reportId) {
        return Optional.of(adminReportClient.listCommentReports())
                .map(ReportedCommentListResponse::items)
                .orElse(List.of())
                .stream()
                .filter(item -> item.reportId() != null && item.reportId() == reportId)
                .findFirst()
                .orElse(null);
    }

    private String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }

    private String toAccessLevelLabel(String accessLevel) {
        if (!StringUtils.hasText(accessLevel)) {
            return "-";
        }
        return switch (accessLevel.toUpperCase(Locale.ROOT)) {
            case "PUBLIC" -> "공개";
            case "PRIVATE" -> "비공개";
            default -> accessLevel;
        };
    }

    private void ensureToastMessages(Model model) {
        if (!model.containsAttribute("toastMessages")) {
            model.addAttribute("toastMessages", List.of());
        }
    }

    private void attachFlashDefaults(Model model) {
        if (!model.containsAttribute("flashStatus")) {
            model.addAttribute("flashStatus", null);
        }
        if (!model.containsAttribute("flashMessage")) {
            model.addAttribute("flashMessage", null);
        }
    }
}
