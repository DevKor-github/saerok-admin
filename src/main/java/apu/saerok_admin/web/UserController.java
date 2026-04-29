package apu.saerok_admin.web;

import apu.saerok_admin.infra.user.AdminUserClient;
import apu.saerok_admin.infra.user.dto.AdminUserListResponse;
import apu.saerok_admin.web.view.Breadcrumb;
import apu.saerok_admin.web.view.CurrentAdminProfile;
import apu.saerok_admin.web.view.UserListItem;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private static final String PERMISSION_ADMIN_ANNOUNCEMENT_WRITE = "ADMIN_ANNOUNCEMENT_WRITE";
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final AdminUserClient adminUserClient;

    @GetMapping
    public String list(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int size,
                       Model model) {
        int normalizedPage = normalizePage(page);
        int normalizedSize = normalizeSize(size);
        boolean canViewUsers = currentAdminProfile != null
                && currentAdminProfile.hasPermission(PERMISSION_ADMIN_ANNOUNCEMENT_WRITE);

        model.addAttribute("pageTitle", "사용자 관리");
        model.addAttribute("activeMenu", "users");
        model.addAttribute("breadcrumbs", List.of(Breadcrumb.of("대시보드", "/"), Breadcrumb.active("사용자")));
        model.addAttribute("toastMessages", List.of());
        model.addAttribute("query", q);
        model.addAttribute("page", normalizedPage);
        model.addAttribute("size", normalizedSize);
        model.addAttribute("canViewUsers", canViewUsers);

        if (!canViewUsers) {
            model.addAttribute("loadError", "사용자 목록을 조회할 권한이 없습니다.");
            addEmptyUserListModel(model);
            return "users/list";
        }

        try {
            AdminUserListResponse response = adminUserClient.listUsers(q, normalizedPage, normalizedSize);
            List<UserListItem> users = response.users() == null
                    ? List.of()
                    : response.users().stream()
                    .map(item -> new UserListItem(item.id(), item.nickname()))
                    .toList();

            model.addAttribute("users", users);
            model.addAttribute("totalPages", response.totalPages());
            model.addAttribute("totalElements", response.totalElements());
            model.addAttribute("pageNumbers", pageNumbers(response.totalPages()));
            model.addAttribute("loadError", null);
        } catch (RestClientResponseException exception) {
            log.warn("Failed to load admin users. status={}, body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            model.addAttribute("loadError", "사용자 목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
            addEmptyUserListModel(model);
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load admin users.", exception);
            model.addAttribute("loadError", "사용자 목록을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.");
            addEmptyUserListModel(model);
        }

        return "users/list";
    }

    private int normalizePage(int page) {
        return page < 1 ? DEFAULT_PAGE : page;
    }

    private int normalizeSize(int size) {
        if (size < 1 || size > MAX_SIZE) {
            return DEFAULT_SIZE;
        }
        return size;
    }

    private List<Integer> pageNumbers(int totalPages) {
        if (totalPages <= 0) {
            return List.of();
        }
        return IntStream.rangeClosed(1, totalPages)
                .boxed()
                .toList();
    }

    private void addEmptyUserListModel(Model model) {
        model.addAttribute("users", List.of());
        model.addAttribute("totalPages", 0);
        model.addAttribute("totalElements", 0L);
        model.addAttribute("pageNumbers", List.of());
    }
}
