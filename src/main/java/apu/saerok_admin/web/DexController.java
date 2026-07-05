package apu.saerok_admin.web;

import apu.saerok_admin.infra.dex.AdminDexClient;
import apu.saerok_admin.infra.dex.dto.AdminBirdDetailResponse;
import apu.saerok_admin.infra.dex.dto.AdminBirdImagePresignRequest;
import apu.saerok_admin.infra.dex.dto.AdminBirdImagePresignResponse;
import apu.saerok_admin.infra.dex.dto.AdminBirdListResponse;
import apu.saerok_admin.infra.dex.dto.AdminBirdUpsertRequest;
import apu.saerok_admin.web.form.DexUpsertForm;
import apu.saerok_admin.web.support.DexFormMapper;
import apu.saerok_admin.web.support.DexOptions;
import apu.saerok_admin.web.view.Breadcrumb;
import apu.saerok_admin.web.view.CurrentAdminProfile;
import apu.saerok_admin.web.view.DexDetail;
import apu.saerok_admin.web.view.DexListItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
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
@RequestMapping("/dex")
public class DexController {

    private final AdminDexClient adminDexClient;
    private final ObjectMapper objectMapper;
    private final DexFormMapper formMapper;
    private final DexOptions dexOptions;

    @GetMapping
    public String list(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int size,
                       Model model) {
        model.addAttribute("pageTitle", "도감 관리");
        model.addAttribute("activeMenu", "dex");
        model.addAttribute("breadcrumbs", List.of(Breadcrumb.of("대시보드", "/"), Breadcrumb.active("도감 관리")));
        model.addAttribute("toastMessages", List.of());
        model.addAttribute("query", q);
        model.addAttribute("page", page);
        model.addAttribute("size", size);

        try {
            AdminBirdListResponse response = adminDexClient.listBirds(q, page, size);
            List<DexListItem> entries = response.birds() == null
                    ? List.of()
                    : response.birds().stream().map(this::toListItem).toList();
            model.addAttribute("entries", entries);
            model.addAttribute("totalPages", response.totalPages());
            model.addAttribute("totalElements", response.totalElements());
            model.addAttribute("page", response.page());
            model.addAttribute("size", response.size());
            model.addAttribute("pageNumbers", pageNumbers(response.totalPages()));
            model.addAttribute("loadError", null);
        } catch (RestClientResponseException exception) {
            log.warn("Failed to load dex birds. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            model.addAttribute("loadError", "도감 목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
            addEmptyListModel(model);
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load dex birds.", exception);
            model.addAttribute("loadError", "도감 목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
            addEmptyListModel(model);
        }

        return "dex/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        prepareForm(model, new DexUpsertForm(), false, null);
        return "dex/form";
    }

    @PostMapping
    public String create(@ModelAttribute("form") DexUpsertForm form,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        AdminBirdUpsertRequest request;
        try {
            request = formMapper.toRequest(form);
        } catch (IllegalArgumentException exception) {
            return renderFormError(model, form, false, null, exception.getMessage());
        }

        try {
            AdminBirdDetailResponse response = adminDexClient.createBird(request);
            redirectAttributes.addFlashAttribute("flashStatus", "success");
            redirectAttributes.addFlashAttribute("flashMessage", "도감이 등록되었습니다.");
            return "redirect:/dex/" + response.id();
        } catch (RestClientResponseException exception) {
            log.warn("Failed to create dex bird. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            return renderFormError(model, form, false, null, backendMessage(exception, "도감 등록에 실패했습니다."));
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to create dex bird.", exception);
            return renderFormError(model, form, false, null, "도감 등록에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    @GetMapping("/{id:\\d+}/edit")
    public String editForm(@PathVariable Long id,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        try {
            AdminBirdDetailResponse response = adminDexClient.getBird(id);
            prepareForm(model, formMapper.toForm(response), true, id);
            return "dex/form";
        } catch (RestClientResponseException exception) {
            log.warn("Failed to load dex bird for edit. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", backendMessage(exception, "도감 정보를 불러오지 못했습니다."));
            return "redirect:/dex";
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load dex bird for edit.", exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "도감 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
            return "redirect:/dex";
        }
    }

    @PostMapping("/{id:\\d+}/edit")
    public String update(@PathVariable Long id,
                         @ModelAttribute("form") DexUpsertForm form,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        AdminBirdUpsertRequest request;
        try {
            request = formMapper.toRequest(form);
        } catch (IllegalArgumentException exception) {
            return renderFormError(model, form, true, id, exception.getMessage());
        }

        try {
            adminDexClient.updateBird(id, request);
            redirectAttributes.addFlashAttribute("flashStatus", "success");
            redirectAttributes.addFlashAttribute("flashMessage", "도감이 수정되었습니다.");
            return "redirect:/dex/" + id;
        } catch (RestClientResponseException exception) {
            log.warn("Failed to update dex bird. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            return renderFormError(model, form, true, id, backendMessage(exception, "도감 수정에 실패했습니다."));
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to update dex bird.", exception);
            return renderFormError(model, form, true, id, "도감 수정에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    @GetMapping("/{id:\\d+}")
    public String detail(@PathVariable Long id,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        try {
            AdminBirdDetailResponse response = adminDexClient.getBird(id);
            model.addAttribute("pageTitle", "도감 상세");
            model.addAttribute("activeMenu", "dex");
            model.addAttribute("breadcrumbs", List.of(
                    Breadcrumb.of("대시보드", "/"),
                    Breadcrumb.of("도감 관리", "/dex"),
                    Breadcrumb.active("#" + id)
            ));
            model.addAttribute("toastMessages", List.of());
            model.addAttribute("detail", toDetail(response));
            return "dex/detail";
        } catch (RestClientResponseException exception) {
            log.warn("Failed to load dex bird detail. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", backendMessage(exception, "도감 상세를 불러오지 못했습니다."));
            return "redirect:/dex";
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load dex bird detail.", exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "도감 상세를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
            return "redirect:/dex";
        }
    }

    @PostMapping("/image/presign")
    @ResponseBody
    public ResponseEntity<?> presignImage(@RequestBody Map<String, String> payload) {
        String contentType = payload != null ? payload.get("contentType") : null;
        if (!StringUtils.hasText(contentType)) {
            return ResponseEntity.badRequest().body(Map.of("message", "contentType이 필요합니다."));
        }

        try {
            AdminBirdImagePresignResponse presign = adminDexClient.generateImagePresignUrl(
                    new AdminBirdImagePresignRequest(contentType));
            return ResponseEntity.ok(Map.of("presignedUrl", presign.presignedUrl(), "objectKey", presign.objectKey()));
        } catch (RestClientResponseException exception) {
            log.warn("Failed to request dex image presign. status={}, body={}", exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            return ResponseEntity.status(exception.getStatusCode())
                    .body(Map.of("message", backendMessage(exception, "Presigned URL 발급에 실패했습니다.")));
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to request dex image presign.", exception);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Presigned URL 발급에 실패했습니다."));
        }
    }

    private String renderFormError(Model model, DexUpsertForm form, boolean edit, Long birdId, String message) {
        form.ensureResidencyRow();
        prepareForm(model, form, edit, birdId);
        model.addAttribute("flashStatus", "error");
        model.addAttribute("flashMessage", message);
        return "dex/form";
    }

    private void prepareForm(Model model, DexUpsertForm form, boolean edit, Long birdId) {
        String activeCrumb = edit ? "#" + birdId + " 수정" : "신규 등록";
        model.addAttribute("pageTitle", edit ? "도감 수정" : "도감 등록");
        model.addAttribute("activeMenu", "dex");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.of("도감 관리", "/dex"),
                Breadcrumb.active(activeCrumb)
        ));
        model.addAttribute("toastMessages", List.of());
        model.addAttribute("form", form);
        model.addAttribute("dexOptions", dexOptions.formModel());
        model.addAttribute("formAction", edit ? "/dex/" + birdId + "/edit" : "/dex");
        model.addAttribute("submitLabel", edit ? "도감 수정" : "도감 등록");
    }

    private DexListItem toListItem(AdminBirdListResponse.Item item) {
        return new DexListItem(
                item.id(),
                item.koreanName(),
                item.scientificName(),
                dexOptions.conservationGradeLabel(item.conservationGrade()),
                item.bodyLengthCm(),
                dexOptions.habitatLabels(item.habitats()),
                item.thumbImageUrl(),
                item.updatedAt()
        );
    }

    private DexDetail toDetail(AdminBirdDetailResponse response) {
        AdminBirdDetailResponse.BirdName name = response.name();
        AdminBirdDetailResponse.BirdTaxonomy taxonomy = response.taxonomy();
        AdminBirdDetailResponse.BirdDescription description = response.description();

        return new DexDetail(
                response.id(),
                name != null ? name.koreanName() : "",
                name != null ? name.scientificName() : "",
                name != null ? name.scientificAuthor() : null,
                name != null ? name.scientificYear() : null,
                taxonomy == null ? null : new DexDetail.Taxonomy(
                        taxonomy.phylumEng(), taxonomy.phylumKor(), taxonomy.classEng(), taxonomy.classKor(),
                        taxonomy.orderEng(), taxonomy.orderKor(), taxonomy.familyEng(), taxonomy.familyKor(),
                        taxonomy.genusEng(), taxonomy.genusKor(), taxonomy.speciesEng(), taxonomy.speciesKor()),
                description != null ? description.description() : null,
                description != null ? description.source() : null,
                description != null ? description.isAiGenerated() : null,
                response.bodyLengthCm(),
                response.nibrUrl(),
                dexOptions.conservationGradeLabel(response.conservationGrade()),
                dexOptions.habitatLabels(response.habitats()),
                response.residencies() == null ? List.of() : response.residencies().stream()
                        .map(residency -> new DexDetail.Residency(
                                dexOptions.residencyLabel(residency.residencyType()),
                                dexOptions.rarityLabel(residency.rarity()),
                                monthsFromBitmask(residency.effectiveMonthBitmask()),
                                residency.monthBitmask() == null
                        ))
                        .toList(),
                response.seasonsWithRarity() == null ? List.of() : response.seasonsWithRarity().stream()
                        .map(season -> new DexDetail.SeasonWithRarity(
                                dexOptions.seasonLabel(season.season()),
                                dexOptions.rarityLabel(season.rarity()),
                                season.priority()
                        ))
                        .toList(),
                response.images() == null ? List.of() : response.images().stream()
                        .map(image -> new DexDetail.Image(
                                image.objectKey(), image.imageUrl(), image.originalUrl(), image.orderIndex(), image.isThumb()))
                        .toList(),
                response.createdAt(),
                response.updatedAt()
        );
    }

    private List<Integer> monthsFromBitmask(Integer bitmask) {
        if (bitmask == null) {
            return List.of();
        }
        return IntStream.rangeClosed(1, 12)
                .filter(month -> (bitmask & (1 << (month - 1))) != 0)
                .boxed()
                .toList();
    }

    private List<Integer> pageNumbers(int totalPages) {
        return totalPages <= 0 ? List.of() : IntStream.rangeClosed(1, totalPages).boxed().toList();
    }

    private void addEmptyListModel(Model model) {
        model.addAttribute("entries", List.of());
        model.addAttribute("totalPages", 0);
        model.addAttribute("totalElements", 0L);
        model.addAttribute("pageNumbers", List.of());
    }

    private String backendMessage(RestClientResponseException exception, String fallback) {
        String body = exception.getResponseBodyAsString();
        if (!StringUtils.hasText(body)) {
            return fallback;
        }
        try {
            String message = objectMapper.readTree(body).path("message").asText();
            return StringUtils.hasText(message) ? message : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
