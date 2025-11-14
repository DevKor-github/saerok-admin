
package apu.saerok_admin.web;

import apu.saerok_admin.infra.ad.AdminAdClient;
import apu.saerok_admin.infra.ad.dto.AdImagePresignResponse;
import apu.saerok_admin.infra.ad.dto.AdminAdImagePresignRequest;
import apu.saerok_admin.infra.ad.dto.AdminAdListResponse;
import apu.saerok_admin.infra.ad.dto.AdminAdPlacementListResponse;
import apu.saerok_admin.infra.ad.dto.AdminCreateAdPlacementRequest;
import apu.saerok_admin.infra.ad.dto.AdminCreateAdRequest;
import apu.saerok_admin.infra.ad.dto.AdminCreateSlotRequest;
import apu.saerok_admin.infra.ad.dto.AdminSlotListResponse;
import apu.saerok_admin.infra.ad.dto.AdminUpdateAdPlacementRequest;
import apu.saerok_admin.infra.ad.dto.AdminUpdateAdRequest;
import apu.saerok_admin.infra.ad.dto.AdminUpdateSlotRequest;
import apu.saerok_admin.web.view.Breadcrumb;
import apu.saerok_admin.web.view.CurrentAdminProfile;
import apu.saerok_admin.web.view.ad.AdForm;
import apu.saerok_admin.web.view.ad.AdListItem;
import apu.saerok_admin.web.view.ad.AdPlacementForm;
import apu.saerok_admin.web.view.ad.AdPlacementGroup;
import apu.saerok_admin.web.view.ad.AdPlacementItem;
import apu.saerok_admin.web.view.ad.AdSlotForm;
import apu.saerok_admin.web.view.ad.AdSlotListItem;
import apu.saerok_admin.web.view.ad.AdSummaryMetrics;
import apu.saerok_admin.web.view.ad.PlacementTimeStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/ads")
public class AdController {

    private static final Logger log = LoggerFactory.getLogger(AdController.class);

    private final AdminAdClient adminAdClient;
    private final Clock clock;

    public AdController(AdminAdClient adminAdClient, Clock clock) {
        this.adminAdClient = adminAdClient;
        this.clock = clock;
    }

    @GetMapping
    public String index(@RequestParam(name = "tab", defaultValue = "ads") String tab,
                        @RequestParam(name = "adQuery", required = false) String adQuery,
                        @RequestParam(name = "slotFilter", required = false) Long slotFilter,
                        @RequestParam(name = "period", defaultValue = "all") String periodFilter,
                        @RequestParam(name = "status", defaultValue = "all") String statusFilter,
                        Model model) {
        model.addAttribute("pageTitle", "광고 관리");
        model.addAttribute("activeMenu", "ads");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.active("광고 관리")
        ));
        model.addAttribute("toastMessages", List.of());

        LocalDate today = LocalDate.now(clock);

        List<AdListItem> adItems = List.of();
        String adsLoadError = null;
        try {
            AdminAdListResponse response = adminAdClient.listAds();
            List<AdminAdListResponse.Item> rawItems = Optional.ofNullable(response)
                    .map(AdminAdListResponse::items)
                    .orElseGet(List::of);
            adItems = rawItems.stream()
                    .map(this::toAdListItem)
                    .sorted(Comparator.comparing(AdListItem::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
        } catch (RestClientResponseException exception) {
            log.warn("Failed to load ads. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            adsLoadError = "광고 목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.";
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load ads.", exception);
            adsLoadError = "광고 목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.";
        }

        List<AdPlacementItem> placementItems = List.of();
        String placementsLoadError = null;
        try {
            AdminAdPlacementListResponse placementResponse = adminAdClient.listPlacements();
            List<AdminAdPlacementListResponse.Item> rawPlacements = Optional.ofNullable(placementResponse)
                    .map(AdminAdPlacementListResponse::items)
                    .orElseGet(List::of);
            placementItems = rawPlacements.stream()
                    .map(item -> toPlacementItem(item, today))
                    .sorted(Comparator.comparing(AdPlacementItem::startDate, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
        } catch (RestClientResponseException exception) {
            log.warn("Failed to load ad placements. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            placementsLoadError = "광고 노출 스케줄을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.";
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load ad placements.", exception);
            placementsLoadError = "광고 노출 스케줄을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.";
        }

        Map<Long, Long> placementCountsBySlot = placementItems.stream()
                .collect(Collectors.groupingBy(AdPlacementItem::slotId, Collectors.counting()));

        List<AdSlotListItem> slotItems = List.of();
        String slotsLoadError = null;
        try {
            AdminSlotListResponse slotResponse = adminAdClient.listSlots();
            List<AdminSlotListResponse.Item> rawSlots = Optional.ofNullable(slotResponse)
                    .map(AdminSlotListResponse::items)
                    .orElseGet(List::of);
            slotItems = rawSlots.stream()
                    .map(item -> toSlotListItem(item, placementCountsBySlot))
                    .sorted(Comparator.comparing(AdSlotListItem::code, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .toList();
        } catch (RestClientResponseException exception) {
            log.warn("Failed to load ad slots. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            slotsLoadError = "광고 위치 목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.";
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load ad slots.", exception);
            slotsLoadError = "광고 위치 목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.";
        }

        List<AdListItem> filteredAds = filterAds(adItems, adQuery);
        List<AdPlacementItem> filteredPlacements = filterPlacements(placementItems, slotFilter, periodFilter, statusFilter, today);
        Map<Long, AdSlotListItem> slotMap = slotItems.stream()
                .collect(Collectors.toMap(AdSlotListItem::id, item -> item, (left, right) -> left, LinkedHashMap::new));

        List<AdPlacementGroup> placementGroups = buildPlacementGroups(slotItems, slotMap, filteredPlacements, slotFilter);

        int activePlacementCount = (int) placementItems.stream()
                .filter(item -> item.enabled() && item.timeStatus() == PlacementTimeStatus.ACTIVE)
                .count();
        AdSummaryMetrics summaryMetrics = new AdSummaryMetrics(adItems.size(), slotItems.size(), activePlacementCount);

        model.addAttribute("tab", normalizeTab(tab));
        model.addAttribute("summary", summaryMetrics);
        model.addAttribute("ads", filteredAds);
        model.addAttribute("adsLoadError", adsLoadError);
        model.addAttribute("adQuery", adQuery != null ? adQuery : "");
        model.addAttribute("slots", slotItems);
        model.addAttribute("slotsLoadError", slotsLoadError);
        model.addAttribute("placementsLoadError", placementsLoadError);
        model.addAttribute("placementGroups", placementGroups);
        model.addAttribute("placementFilterSlot", slotFilter);
        model.addAttribute("placementFilterPeriod", normalizePeriod(periodFilter));
        model.addAttribute("placementFilterStatus", normalizeStatus(statusFilter));
        model.addAttribute("hasPlacements", !filteredPlacements.isEmpty());
        model.addAttribute("toastMessages", List.of());

        return "ads/list";
    }

    @GetMapping("/new")
    public String newAdForm(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                            Model model,
                            RedirectAttributes redirectAttributes) {
        if (!currentAdminProfile.isAdminEditor()) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고를 등록할 권한이 없습니다.");
            return "redirect:/ads";
        }

        model.addAttribute("pageTitle", "새 광고 등록");
        model.addAttribute("activeMenu", "ads");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.of("광고 관리", "/ads"),
                Breadcrumb.active("새 광고 등록")
        ));
        model.addAttribute("toastMessages", List.of());
        model.addAttribute("form", new AdForm(null, "", "", "", null, null, null));
        return "ads/ad-form";
    }

    @PostMapping
    public String createAd(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                           @RequestParam String name,
                           @RequestParam(name = "targetUrl") String targetUrl,
                           @RequestParam(name = "memo", required = false) String memo,
                           @RequestParam(name = "objectKey") String objectKey,
                           @RequestParam(name = "contentType") String contentType,
                           RedirectAttributes redirectAttributes) {
        if (!currentAdminProfile.isAdminEditor()) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고를 등록할 권한이 없습니다.");
            return "redirect:/ads?tab=ads";
        }

        if (!StringUtils.hasText(name) || !StringUtils.hasText(targetUrl) || !StringUtils.hasText(objectKey) || !StringUtils.hasText(contentType)) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "필수 입력값을 모두 채워주세요.");
            return "redirect:/ads/new";
        }

        try {
            adminAdClient.createAd(new AdminCreateAdRequest(name.trim(), trimToNull(memo), objectKey, contentType, targetUrl.trim()));
            redirectAttributes.addFlashAttribute("flashStatus", "success");
            redirectAttributes.addFlashAttribute("flashMessage", "저장되었습니다.");
            return "redirect:/ads?tab=ads";
        } catch (RestClientResponseException exception) {
            log.warn("Failed to create ad. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고 등록에 실패했습니다. 잠시 후 다시 시도해주세요.");
            return "redirect:/ads/new";
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to create ad.", exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고 등록에 실패했습니다. 잠시 후 다시 시도해주세요.");
            return "redirect:/ads/new";
        }
    }

    @GetMapping("/edit")
    public String editAdForm(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                             @RequestParam("id") Long adId,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (!currentAdminProfile.isAdminEditor()) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고를 수정할 권한이 없습니다.");
            return "redirect:/ads";
        }

        try {
            AdminAdListResponse response = adminAdClient.listAds();
            Optional<AdminAdListResponse.Item> match = Optional.ofNullable(response)
                    .map(AdminAdListResponse::items)
                    .orElseGet(List::of)
                    .stream()
                    .filter(item -> Objects.equals(item.id(), adId))
                    .findFirst();
            if (match.isEmpty()) {
                redirectAttributes.addFlashAttribute("flashStatus", "error");
                redirectAttributes.addFlashAttribute("flashMessage", "해당 광고를 찾을 수 없습니다.");
                return "redirect:/ads";
            }

            AdminAdListResponse.Item item = match.get();
            AdForm form = new AdForm(item.id(), item.name(), item.targetUrl(), item.memo(), null, null, item.imageUrl());

            model.addAttribute("pageTitle", "광고 수정");
            model.addAttribute("activeMenu", "ads");
            model.addAttribute("breadcrumbs", List.of(
                    Breadcrumb.of("대시보드", "/"),
                    Breadcrumb.of("광고 관리", "/ads"),
                    Breadcrumb.active("광고 수정")
            ));
            model.addAttribute("toastMessages", List.of());
            model.addAttribute("form", form);
            return "ads/ad-form";
        } catch (RestClientResponseException exception) {
            log.warn("Failed to load ad for edit. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load ad for edit.", exception);
        }

        redirectAttributes.addFlashAttribute("flashStatus", "error");
        redirectAttributes.addFlashAttribute("flashMessage", "광고 정보를 불러오지 못했습니다.");
        return "redirect:/ads";
    }

    @PostMapping("/edit")
    public String updateAd(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                           @RequestParam("id") Long adId,
                           @RequestParam String name,
                           @RequestParam("targetUrl") String targetUrl,
                           @RequestParam(name = "memo", required = false) String memo,
                           @RequestParam(name = "objectKey", required = false) String objectKey,
                           @RequestParam(name = "contentType", required = false) String contentType,
                           RedirectAttributes redirectAttributes) {
        if (!currentAdminProfile.isAdminEditor()) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고를 수정할 권한이 없습니다.");
            return "redirect:/ads?tab=ads";
        }

        if (!StringUtils.hasText(name) || !StringUtils.hasText(targetUrl)) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "필수 입력값을 모두 채워주세요.");
            return "redirect:/ads/edit?id=" + adId;
        }

        String normalizedObjectKey = StringUtils.hasText(objectKey) ? objectKey : null;
        String normalizedContentType = StringUtils.hasText(contentType) ? contentType : null;

        try {
            adminAdClient.updateAd(adId, new AdminUpdateAdRequest(name.trim(), trimToNull(memo), normalizedObjectKey, normalizedContentType, targetUrl.trim()));
            redirectAttributes.addFlashAttribute("flashStatus", "success");
            redirectAttributes.addFlashAttribute("flashMessage", "저장되었습니다.");
            return "redirect:/ads?tab=ads";
        } catch (RestClientResponseException exception) {
            log.warn("Failed to update ad. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to update ad.", exception);
        }

        redirectAttributes.addFlashAttribute("flashStatus", "error");
        redirectAttributes.addFlashAttribute("flashMessage", "광고 수정에 실패했습니다. 잠시 후 다시 시도해주세요.");
        return "redirect:/ads/edit?id=" + adId;
    }

    @PostMapping("/delete")
    public String deleteAd(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                           @RequestParam("id") Long adId,
                           RedirectAttributes redirectAttributes) {
        if (!currentAdminProfile.isAdminEditor()) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고를 삭제할 권한이 없습니다.");
            return "redirect:/ads?tab=ads";
        }

        try {
            adminAdClient.deleteAd(adId);
            redirectAttributes.addFlashAttribute("flashStatus", "success");
            redirectAttributes.addFlashAttribute("flashMessage", "삭제되었습니다.");
        } catch (RestClientResponseException exception) {
            log.warn("Failed to delete ad. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고 삭제에 실패했습니다. 잠시 후 다시 시도해주세요.");
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to delete ad.", exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고 삭제에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }

        return "redirect:/ads?tab=ads";
    }

    @GetMapping("/slots/new")
    public String newSlotForm(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        if (!currentAdminProfile.isAdminEditor()) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고 위치를 등록할 권한이 없습니다.");
            return "redirect:/ads?tab=slots";
        }

        model.addAttribute("pageTitle", "새 광고 위치 추가");
        model.addAttribute("activeMenu", "ads");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.of("광고 관리", "/ads?tab=slots"),
                Breadcrumb.active("새 광고 위치 추가")
        ));
        model.addAttribute("toastMessages", List.of());
        model.addAttribute("form", new AdSlotForm(null, "", "", 50.0, 60));
        return "ads/slot-form";
    }

    @PostMapping("/slots")
    public String createSlot(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                             @RequestParam("code") String code,
                             @RequestParam(name = "description", required = false) String description,
                             @RequestParam("fallbackRatio") Double fallbackRatioPercent,
                             @RequestParam("ttlSeconds") Integer ttlSeconds,
                             RedirectAttributes redirectAttributes) {
        if (!currentAdminProfile.isAdminEditor()) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고 위치를 등록할 권한이 없습니다.");
            return "redirect:/ads?tab=slots";
        }

        if (!StringUtils.hasText(code) || fallbackRatioPercent == null || ttlSeconds == null) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "필수 입력값을 모두 채워주세요.");
            return "redirect:/ads/slots/new";
        }

        double normalizedFallback = Math.max(0, Math.min(100, fallbackRatioPercent));
        double fallbackRatio = normalizedFallback / 100.0;

        try {
            adminAdClient.createSlot(new AdminCreateSlotRequest(code.trim(), trimToNull(description), fallbackRatio, ttlSeconds));
            redirectAttributes.addFlashAttribute("flashStatus", "success");
            redirectAttributes.addFlashAttribute("flashMessage", "저장되었습니다.");
            return "redirect:/ads?tab=slots";
        } catch (RestClientResponseException exception) {
            log.warn("Failed to create slot. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to create slot.", exception);
        }

        redirectAttributes.addFlashAttribute("flashStatus", "error");
        redirectAttributes.addFlashAttribute("flashMessage", "광고 위치 등록에 실패했습니다. 잠시 후 다시 시도해주세요.");
        return "redirect:/ads/slots/new";
    }

    @GetMapping("/slots/edit")
    public String editSlotForm(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                               @RequestParam("id") Long slotId,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        if (!currentAdminProfile.isAdminEditor()) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고 위치를 수정할 권한이 없습니다.");
            return "redirect:/ads?tab=slots";
        }

        try {
            AdminSlotListResponse response = adminAdClient.listSlots();
            Optional<AdminSlotListResponse.Item> match = Optional.ofNullable(response)
                    .map(AdminSlotListResponse::items)
                    .orElseGet(List::of)
                    .stream()
                    .filter(item -> Objects.equals(item.id(), slotId))
                    .findFirst();
            if (match.isEmpty()) {
                redirectAttributes.addFlashAttribute("flashStatus", "error");
                redirectAttributes.addFlashAttribute("flashMessage", "해당 광고 위치를 찾을 수 없습니다.");
                return "redirect:/ads?tab=slots";
            }

            AdminSlotListResponse.Item item = match.get();
            double fallbackRatioPercent = item.fallbackRatio() != null ? item.fallbackRatio() * 100.0 : 0.0;
            AdSlotForm form = new AdSlotForm(item.id(), item.name(), item.memo(), fallbackRatioPercent, item.ttlSeconds());

            model.addAttribute("pageTitle", "광고 위치 수정");
            model.addAttribute("activeMenu", "ads");
            model.addAttribute("breadcrumbs", List.of(
                    Breadcrumb.of("대시보드", "/"),
                    Breadcrumb.of("광고 관리", "/ads?tab=slots"),
                    Breadcrumb.active("광고 위치 수정")
            ));
            model.addAttribute("toastMessages", List.of());
            model.addAttribute("form", form);
            return "ads/slot-form";
        } catch (RestClientResponseException exception) {
            log.warn("Failed to load slot for edit. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load slot for edit.", exception);
        }

        redirectAttributes.addFlashAttribute("flashStatus", "error");
        redirectAttributes.addFlashAttribute("flashMessage", "광고 위치 정보를 불러오지 못했습니다.");
        return "redirect:/ads?tab=slots";
    }

    @PostMapping("/slots/edit")
    public String updateSlot(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                             @RequestParam("id") Long slotId,
                             @RequestParam(name = "description", required = false) String description,
                             @RequestParam("fallbackRatio") Double fallbackRatioPercent,
                             @RequestParam("ttlSeconds") Integer ttlSeconds,
                             RedirectAttributes redirectAttributes) {
        if (!currentAdminProfile.isAdminEditor()) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고 위치를 수정할 권한이 없습니다.");
            return "redirect:/ads?tab=slots";
        }

        if (fallbackRatioPercent == null || ttlSeconds == null) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "필수 입력값을 모두 채워주세요.");
            return "redirect:/ads/slots/edit?id=" + slotId;
        }

        double normalizedFallback = Math.max(0, Math.min(100, fallbackRatioPercent));
        double fallbackRatio = normalizedFallback / 100.0;

        try {
            adminAdClient.updateSlot(slotId, new AdminUpdateSlotRequest(trimToNull(description), fallbackRatio, ttlSeconds));
            redirectAttributes.addFlashAttribute("flashStatus", "success");
            redirectAttributes.addFlashAttribute("flashMessage", "저장되었습니다.");
            return "redirect:/ads?tab=slots";
        } catch (RestClientResponseException exception) {
            log.warn("Failed to update slot. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to update slot.", exception);
        }

        redirectAttributes.addFlashAttribute("flashStatus", "error");
        redirectAttributes.addFlashAttribute("flashMessage", "광고 위치 수정에 실패했습니다. 잠시 후 다시 시도해주세요.");
        return "redirect:/ads/slots/edit?id=" + slotId;
    }

    @PostMapping("/slots/delete")
    public String deleteSlot(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                             @RequestParam("id") Long slotId,
                             RedirectAttributes redirectAttributes) {
        if (!currentAdminProfile.isAdminEditor()) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고 위치를 삭제할 권한이 없습니다.");
            return "redirect:/ads?tab=slots";
        }

        try {
            adminAdClient.deleteSlot(slotId);
            redirectAttributes.addFlashAttribute("flashStatus", "success");
            redirectAttributes.addFlashAttribute("flashMessage", "삭제되었습니다.");
        } catch (RestClientResponseException exception) {
            log.warn("Failed to delete slot. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고 위치 삭제에 실패했습니다. 잠시 후 다시 시도해주세요.");
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to delete slot.", exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고 위치 삭제에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }

        return "redirect:/ads?tab=slots";
    }

    @GetMapping("/placements/new")
    public String newPlacementForm(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                                   @RequestParam(name = "slotId", required = false) Long preselectedSlotId,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        if (!currentAdminProfile.isAdminEditor()) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고 노출 스케줄을 등록할 권한이 없습니다.");
            return "redirect:/ads?tab=placements";
        }

        List<AdListItem> ads = fetchAdOptions();
        List<AdSlotListItem> slots = fetchSlotOptions();

        model.addAttribute("pageTitle", "새 스케줄 등록");
        model.addAttribute("activeMenu", "ads");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.of("광고 관리", "/ads?tab=placements"),
                Breadcrumb.active("새 스케줄 등록")
        ));
        model.addAttribute("toastMessages", List.of());
        model.addAttribute("form", new AdPlacementForm(null, null, preselectedSlotId, null, null, (short) 1, Boolean.TRUE));
        model.addAttribute("adOptions", ads);
        model.addAttribute("slotOptions", slots);
        return "ads/placement-form";
    }

    @PostMapping("/placements")
    public String createPlacement(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                                  @RequestParam("adId") Long adId,
                                  @RequestParam("slotId") Long slotId,
                                  @RequestParam("startDate") String startDate,
                                  @RequestParam("endDate") String endDate,
                                  @RequestParam("weight") Short weight,
                                  @RequestParam(name = "enabled", defaultValue = "false") boolean enabled,
                                  RedirectAttributes redirectAttributes) {
        if (!currentAdminProfile.isAdminEditor()) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고 노출 스케줄을 등록할 권한이 없습니다.");
            return "redirect:/ads?tab=placements";
        }

        try {
            AdminCreateAdPlacementRequest request = new AdminCreateAdPlacementRequest(
                    adId,
                    slotId,
                    LocalDate.parse(startDate),
                    LocalDate.parse(endDate),
                    weight,
                    enabled
            );
            adminAdClient.createPlacement(request);
            redirectAttributes.addFlashAttribute("flashStatus", "success");
            redirectAttributes.addFlashAttribute("flashMessage", "저장되었습니다.");
            return "redirect:/ads?tab=placements";
        } catch (Exception exception) {
            log.warn("Failed to create placement.", exception);
        }

        redirectAttributes.addFlashAttribute("flashStatus", "error");
        redirectAttributes.addFlashAttribute("flashMessage", "광고 노출 스케줄 등록에 실패했습니다. 입력값을 확인해주세요.");
        return "redirect:/ads/placements/new";
    }

    @GetMapping("/placements/edit")
    public String editPlacementForm(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                                    @RequestParam("id") Long placementId,
                                    Model model,
                                    RedirectAttributes redirectAttributes) {
        if (!currentAdminProfile.isAdminEditor()) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고 노출 스케줄을 수정할 권한이 없습니다.");
            return "redirect:/ads?tab=placements";
        }

        try {
            AdminAdPlacementListResponse response = adminAdClient.listPlacements();
            Optional<AdminAdPlacementListResponse.Item> match = Optional.ofNullable(response)
                    .map(AdminAdPlacementListResponse::items)
                    .orElseGet(List::of)
                    .stream()
                    .filter(item -> Objects.equals(item.id(), placementId))
                    .findFirst();
            if (match.isEmpty()) {
                redirectAttributes.addFlashAttribute("flashStatus", "error");
                redirectAttributes.addFlashAttribute("flashMessage", "해당 스케줄을 찾을 수 없습니다.");
                return "redirect:/ads?tab=placements";
            }

            AdminAdPlacementListResponse.Item item = match.get();
            AdPlacementForm form = new AdPlacementForm(
                    item.id(),
                    item.adId(),
                    item.slotId(),
                    item.startDate() != null ? item.startDate().toString() : null,
                    item.endDate() != null ? item.endDate().toString() : null,
                    item.weight() != null ? item.weight() : (short) 1,
                    item.enabled()
            );

            model.addAttribute("pageTitle", "스케줄 수정");
            model.addAttribute("activeMenu", "ads");
            model.addAttribute("breadcrumbs", List.of(
                    Breadcrumb.of("대시보드", "/"),
                    Breadcrumb.of("광고 관리", "/ads?tab=placements"),
                    Breadcrumb.active("스케줄 수정")
            ));
            model.addAttribute("toastMessages", List.of());
            model.addAttribute("form", form);
            model.addAttribute("adOptions", fetchAdOptions());
            model.addAttribute("slotOptions", fetchSlotOptions());
            return "ads/placement-form";
        } catch (RestClientResponseException exception) {
            log.warn("Failed to load placement for edit. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load placement for edit.", exception);
        }

        redirectAttributes.addFlashAttribute("flashStatus", "error");
        redirectAttributes.addFlashAttribute("flashMessage", "광고 노출 스케줄 정보를 불러오지 못했습니다.");
        return "redirect:/ads?tab=placements";
    }

    @PostMapping("/placements/edit")
    public String updatePlacement(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                                  @RequestParam("id") Long placementId,
                                  @RequestParam("slotId") Long slotId,
                                  @RequestParam("startDate") String startDate,
                                  @RequestParam("endDate") String endDate,
                                  @RequestParam("weight") Short weight,
                                  @RequestParam(name = "enabled", defaultValue = "false") boolean enabled,
                                  RedirectAttributes redirectAttributes) {
        if (!currentAdminProfile.isAdminEditor()) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고 노출 스케줄을 수정할 권한이 없습니다.");
            return "redirect:/ads?tab=placements";
        }

        try {
            AdminUpdateAdPlacementRequest request = new AdminUpdateAdPlacementRequest(
                    slotId,
                    LocalDate.parse(startDate),
                    LocalDate.parse(endDate),
                    weight,
                    enabled
            );
            adminAdClient.updatePlacement(placementId, request);
            redirectAttributes.addFlashAttribute("flashStatus", "success");
            redirectAttributes.addFlashAttribute("flashMessage", "저장되었습니다.");
            return "redirect:/ads?tab=placements";
        } catch (Exception exception) {
            log.warn("Failed to update placement.", exception);
        }

        redirectAttributes.addFlashAttribute("flashStatus", "error");
        redirectAttributes.addFlashAttribute("flashMessage", "광고 노출 스케줄 수정에 실패했습니다. 입력값을 확인해주세요.");
        return "redirect:/ads/placements/edit?id=" + placementId;
    }

    @PostMapping("/placements/delete")
    public String deletePlacement(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                                  @RequestParam("id") Long placementId,
                                  RedirectAttributes redirectAttributes) {
        if (!currentAdminProfile.isAdminEditor()) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고 노출 스케줄을 삭제할 권한이 없습니다.");
            return "redirect:/ads?tab=placements";
        }

        try {
            adminAdClient.deletePlacement(placementId);
            redirectAttributes.addFlashAttribute("flashStatus", "success");
            redirectAttributes.addFlashAttribute("flashMessage", "삭제되었습니다.");
        } catch (RestClientResponseException exception) {
            log.warn("Failed to delete placement. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고 노출 스케줄 삭제에 실패했습니다. 잠시 후 다시 시도해주세요.");
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to delete placement.", exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고 노출 스케줄 삭제에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }

        return "redirect:/ads?tab=placements";
    }

    @PostMapping("/placements/toggle")
    public String togglePlacement(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                                  @RequestParam("id") Long placementId,
                                  @RequestParam("enabled") boolean enabled,
                                  RedirectAttributes redirectAttributes) {
        if (!currentAdminProfile.isAdminEditor()) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고 노출 상태를 변경할 권한이 없습니다.");
            return "redirect:/ads?tab=placements";
        }

        try {
            AdminAdPlacementListResponse response = adminAdClient.listPlacements();
            Optional<AdminAdPlacementListResponse.Item> match = Optional.ofNullable(response)
                    .map(AdminAdPlacementListResponse::items)
                    .orElseGet(List::of)
                    .stream()
                    .filter(item -> Objects.equals(item.id(), placementId))
                    .findFirst();
            if (match.isEmpty()) {
                redirectAttributes.addFlashAttribute("flashStatus", "error");
                redirectAttributes.addFlashAttribute("flashMessage", "해당 스케줄을 찾을 수 없습니다.");
                return "redirect:/ads?tab=placements";
            }

            AdminAdPlacementListResponse.Item item = match.get();
            AdminUpdateAdPlacementRequest request = new AdminUpdateAdPlacementRequest(
                    item.slotId(),
                    item.startDate(),
                    item.endDate(),
                    item.weight(),
                    enabled
            );
            adminAdClient.updatePlacement(placementId, request);
            redirectAttributes.addFlashAttribute("flashStatus", "success");
            redirectAttributes.addFlashAttribute("flashMessage", enabled ? "이 스케줄을 다시 활성화합니다." : "저장되었습니다.");
        } catch (RestClientResponseException exception) {
            log.warn("Failed to toggle placement. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고 노출 상태 변경에 실패했습니다. 잠시 후 다시 시도해주세요.");
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to toggle placement.", exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "광고 노출 상태 변경에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }

        return "redirect:/ads?tab=placements";
    }

    @PostMapping("/image/presign")
    @ResponseBody
    public ResponseEntity<?> presignImage(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                                          @RequestBody Map<String, String> payload) {
        if (!currentAdminProfile.isAdminEditor()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "이미지 업로드 권한이 없습니다."));
        }

        String contentType = payload != null ? payload.get("contentType") : null;
        if (!StringUtils.hasText(contentType)) {
            return ResponseEntity.badRequest().body(Map.of("message", "contentType이 필요합니다."));
        }

        try {
            AdImagePresignResponse presign = adminAdClient.generateAdImagePresignUrl(new AdminAdImagePresignRequest(contentType));
            return ResponseEntity.ok(Map.of(
                    "presignedUrl", presign.presignedUrl(),
                    "objectKey", presign.objectKey()
            ));
        } catch (RestClientResponseException exception) {
            log.warn("Failed to request ad image presign. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", "Presigned URL 발급에 실패했습니다."));
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to request ad image presign.", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Presigned URL 발급에 실패했습니다."));
        }
    }

    private String normalizeTab(String tab) {
        if (!StringUtils.hasText(tab)) {
            return "ads";
        }
        return switch (tab.toLowerCase(Locale.ROOT)) {
            case "ads", "slots", "placements" -> tab.toLowerCase(Locale.ROOT);
            default -> "ads";
        };
    }

    private String normalizePeriod(String period) {
        if (!StringUtils.hasText(period)) {
            return "all";
        }
        return switch (period.toLowerCase(Locale.ROOT)) {
            case "today" -> "today";
            case "next7", "seven", "upcoming" -> "next7";
            default -> "all";
        };
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "all";
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "active" -> "active";
            case "upcoming" -> "upcoming";
            case "ended", "past" -> "ended";
            default -> "all";
        };
    }

    private List<AdListItem> filterAds(List<AdListItem> ads, String query) {
        if (!StringUtils.hasText(query)) {
            return ads;
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        return ads.stream()
                .filter(item -> containsIgnoreCase(item.name(), normalized)
                        || containsIgnoreCase(item.targetUrl(), normalized)
                        || containsIgnoreCase(item.memo(), normalized))
                .toList();
    }

    private List<AdPlacementItem> filterPlacements(List<AdPlacementItem> placements,
                                                   Long slotFilter,
                                                   String periodFilter,
                                                   String statusFilter,
                                                   LocalDate today) {
        String normalizedPeriod = normalizePeriod(periodFilter);
        String normalizedStatus = normalizeStatus(statusFilter);
        return placements.stream()
                .filter(item -> slotFilter == null || Objects.equals(item.slotId(), slotFilter))
                .filter(item -> matchesPeriod(item, normalizedPeriod, today))
                .filter(item -> matchesStatus(item, normalizedStatus))
                .sorted(Comparator.comparing(AdPlacementItem::startDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private boolean matchesPeriod(AdPlacementItem item, String period, LocalDate today) {
        if ("today".equals(period)) {
            if (item.startDate() == null || item.endDate() == null) {
                return false;
            }
            return !today.isBefore(item.startDate()) && !today.isAfter(item.endDate());
        }
        if ("next7".equals(period)) {
            if (item.startDate() == null || item.endDate() == null) {
                return false;
            }
            LocalDate rangeEnd = today.plusDays(7);
            return !item.endDate().isBefore(today) && !item.startDate().isAfter(rangeEnd);
        }
        return true;
    }

    private boolean matchesStatus(AdPlacementItem item, String status) {
        return switch (status) {
            case "active" -> item.timeStatus() == PlacementTimeStatus.ACTIVE;
            case "upcoming" -> item.timeStatus() == PlacementTimeStatus.UPCOMING;
            case "ended" -> item.timeStatus() == PlacementTimeStatus.ENDED;
            default -> true;
        };
    }

    private boolean containsIgnoreCase(String text, String query) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(query)) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).contains(query);
    }

    private AdListItem toAdListItem(AdminAdListResponse.Item item) {
        return new AdListItem(
                item.id(),
                item.name(),
                item.memo(),
                item.imageUrl(),
                item.contentType(),
                item.targetUrl(),
                item.createdAt(),
                item.updatedAt()
        );
    }

    private AdSlotListItem toSlotListItem(AdminSlotListResponse.Item item, Map<Long, Long> placementCountsBySlot) {
        double fallbackRatioPercent = item.fallbackRatio() != null ? item.fallbackRatio() * 100.0 : 0.0;
        int ttlSeconds = item.ttlSeconds() != null ? item.ttlSeconds() : 0;
        int connectedCount = placementCountsBySlot.getOrDefault(item.id(), 0L).intValue();
        return new AdSlotListItem(
                item.id(),
                item.name(),
                item.memo(),
                fallbackRatioPercent,
                ttlSeconds,
                item.createdAt(),
                item.updatedAt(),
                connectedCount
        );
    }

    private AdPlacementItem toPlacementItem(AdminAdPlacementListResponse.Item item, LocalDate today) {
        PlacementTimeStatus timeStatus = resolveTimeStatus(item, today);
        boolean enabled = Boolean.TRUE.equals(item.enabled());
        short weight = item.weight() != null ? item.weight() : (short) 1;
        return new AdPlacementItem(
                item.id(),
                item.adId(),
                item.adName(),
                item.adImageUrl(),
                item.slotId(),
                item.slotName(),
                item.startDate(),
                item.endDate(),
                weight,
                enabled,
                item.createdAt(),
                item.updatedAt(),
                timeStatus
        );
    }

    private PlacementTimeStatus resolveTimeStatus(AdminAdPlacementListResponse.Item item, LocalDate today) {
        LocalDate start = item.startDate();
        LocalDate end = item.endDate();
        if (start == null || end == null) {
            return PlacementTimeStatus.ACTIVE;
        }
        if (today.isBefore(start)) {
            return PlacementTimeStatus.UPCOMING;
        }
        if (today.isAfter(end)) {
            return PlacementTimeStatus.ENDED;
        }
        return PlacementTimeStatus.ACTIVE;
    }

    private List<AdPlacementGroup> buildPlacementGroups(List<AdSlotListItem> slotItems,
                                                        Map<Long, AdSlotListItem> slotMap,
                                                        List<AdPlacementItem> filteredPlacements,
                                                        Long slotFilter) {
        Map<Long, List<AdPlacementItem>> placementsBySlot = filteredPlacements.stream()
                .collect(Collectors.groupingBy(AdPlacementItem::slotId, LinkedHashMap::new, Collectors.toCollection(ArrayList::new)));

        List<AdPlacementGroup> groups = new ArrayList<>();
        for (AdSlotListItem slot : slotItems) {
            if (slotFilter != null && !Objects.equals(slot.id(), slotFilter)) {
                continue;
            }
            List<AdPlacementItem> placements = new ArrayList<>(placementsBySlot.getOrDefault(slot.id(), List.of()));
            placements.sort(Comparator.comparing(AdPlacementItem::startDate, Comparator.nullsLast(Comparator.naturalOrder())));
            groups.add(new AdPlacementGroup(slot.id(), slot.code(), slot.description(), List.copyOf(placements)));
        }

        // Placements whose slot is missing from slot list
        placementsBySlot.forEach((slotId, items) -> {
            if (slotId == null || slotMap.containsKey(slotId)) {
                return;
            }
            if (slotFilter != null && !Objects.equals(slotId, slotFilter)) {
                return;
            }
            items.sort(Comparator.comparing(AdPlacementItem::startDate, Comparator.nullsLast(Comparator.naturalOrder())));
            AdPlacementItem sample = items.get(0);
            groups.add(new AdPlacementGroup(slotId, sample.slotName(), null, List.copyOf(items)));
        });

        return groups;
    }

    private List<AdListItem> fetchAdOptions() {
        try {
            AdminAdListResponse response = adminAdClient.listAds();
            return Optional.ofNullable(response)
                    .map(AdminAdListResponse::items)
                    .orElseGet(List::of)
                    .stream()
                    .map(this::toAdListItem)
                    .sorted(Comparator.comparing(AdListItem::name, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .toList();
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load ads for options.", exception);
            return List.of();
        }
    }

    private List<AdSlotListItem> fetchSlotOptions() {
        try {
            AdminSlotListResponse response = adminAdClient.listSlots();
            return Optional.ofNullable(response)
                    .map(AdminSlotListResponse::items)
                    .orElseGet(List::of)
                    .stream()
                    .map(item -> toSlotListItem(item, Map.of()))
                    .sorted(Comparator.comparing(AdSlotListItem::code, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .toList();
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load slots for options.", exception);
            return List.of();
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}


