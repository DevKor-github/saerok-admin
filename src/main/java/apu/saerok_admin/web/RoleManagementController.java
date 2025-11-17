package apu.saerok_admin.web;

import apu.saerok_admin.infra.role.AdminRoleClient;
import apu.saerok_admin.infra.role.dto.AdminMyRoleResponse;
import apu.saerok_admin.infra.role.dto.AdminRoleListResponse;
import apu.saerok_admin.infra.role.dto.AdminRoleUserListResponse;
import apu.saerok_admin.infra.role.dto.AdminUserRoleResponse;
import apu.saerok_admin.infra.role.dto.AssignRoleRequest;
import apu.saerok_admin.infra.role.dto.CreateRoleRequest;
import apu.saerok_admin.infra.role.dto.PermissionSummaryResponse;
import apu.saerok_admin.infra.role.dto.RoleDetailResponse;
import apu.saerok_admin.infra.role.dto.RoleSummaryResponse;
import apu.saerok_admin.infra.role.dto.UpdateRolePermissionsRequest;
import apu.saerok_admin.web.view.Breadcrumb;
import apu.saerok_admin.web.view.CurrentAdminProfile;
import apu.saerok_admin.web.view.role.PermissionCatalog;
import apu.saerok_admin.web.view.role.PermissionOptionView;
import apu.saerok_admin.web.view.role.PermissionView;
import apu.saerok_admin.web.view.role.RoleDisplay;
import apu.saerok_admin.web.view.role.RolePermissionGroupView;
import apu.saerok_admin.web.view.role.RoleTemplateView;
import apu.saerok_admin.web.view.role.TeamMemberView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequestMapping("/admin/roles")
public class RoleManagementController {

    private static final Logger log = LoggerFactory.getLogger(RoleManagementController.class);
    private static final String TAB_MY = "my";
    private static final String TAB_TEAM = "team";
    private static final String TAB_MANAGE = "manage";
    private static final String PERMISSION_ADMIN_ROLE_MY_READ = "ADMIN_ROLE_MY_READ";
    private static final String PERMISSION_ADMIN_ROLE_READ = "ADMIN_ROLE_READ";
    private static final String PERMISSION_ADMIN_ROLE_WRITE = "ADMIN_ROLE_WRITE";

    private final AdminRoleClient adminRoleClient;

    public RoleManagementController(AdminRoleClient adminRoleClient) {
        this.adminRoleClient = adminRoleClient;
    }

    @GetMapping
    public String index(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                        @RequestParam(name = "tab", required = false) String tab,
                        @RequestParam(name = "selectedRoleCode", required = false) String selectedRoleCode,
                        Model model) {
        model.addAttribute("pageTitle", "운영 권한");
        model.addAttribute("activeMenu", "adminRoles");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.active("운영 권한")
        ));
        model.addAttribute("toastMessages", List.of());

        boolean canViewMyRoles = currentAdminProfile.hasPermission(PERMISSION_ADMIN_ROLE_MY_READ);
        boolean canViewTeamRoles = currentAdminProfile.hasPermission(PERMISSION_ADMIN_ROLE_READ);
        boolean canManageRoles = currentAdminProfile.hasPermission(PERMISSION_ADMIN_ROLE_WRITE);

        List<RoleTemplateView> roleTemplates = List.of();
        Map<String, RoleTemplateView> roleTemplatesByCode = Map.of();
        List<PermissionOptionView> permissionOptions = List.of();
        String roleTemplatesLoadError = null;

        if (canViewTeamRoles || canManageRoles) {
            try {
                AdminRoleListResponse response = adminRoleClient.listRoles();
                roleTemplates = Optional.ofNullable(response)
                        .map(AdminRoleListResponse::roles)
                        .orElseGet(List::of)
                        .stream()
                        .map(this::toRoleTemplateView)
                        .sorted(roleTemplateComparator())
                        .toList();
                roleTemplatesByCode = roleTemplates.stream()
                        .collect(Collectors.toMap(
                                RoleTemplateView::code,
                                template -> template,
                                (left, right) -> left,
                                LinkedHashMap::new
                        ));
                permissionOptions = buildPermissionOptions(roleTemplates);
            } catch (RestClientResponseException exception) {
                log.warn("Failed to load role templates. status={}, body={}",
                        exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
                roleTemplatesLoadError = "ROLE 템플릿을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
            } catch (RestClientException | IllegalStateException exception) {
                log.warn("Failed to load role templates.", exception);
                roleTemplatesLoadError = "ROLE 템플릿을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
            }
        }

        String normalizedRoleCode = normalizeRoleCode(selectedRoleCode);
        RoleTemplateView selectedRoleTemplate = roleTemplatesByCode.get(normalizedRoleCode);
        if (selectedRoleTemplate == null && !roleTemplates.isEmpty()) {
            selectedRoleTemplate = roleTemplates.get(0);
            normalizedRoleCode = selectedRoleTemplate.code();
        }

        List<RoleDisplay> myRoles = List.of();
        List<RolePermissionGroupView> myRolePermissionGroups = List.of();
        String myRolesLoadError = null;

        if (canViewMyRoles) {
            try {
                AdminMyRoleResponse myRoleResponse = adminRoleClient.getMyRoles();
                myRoles = Optional.ofNullable(myRoleResponse)
                        .map(AdminMyRoleResponse::roles)
                        .orElseGet(List::of)
                        .stream()
                        .map(this::toRoleDisplay)
                        .toList();
                Map<String, RoleTemplateView> mapping = roleTemplatesByCode;
                myRolePermissionGroups = myRoles.stream()
                        .map(role -> new RolePermissionGroupView(role, resolvePermissions(role.code(), mapping)))
                        .toList();
            } catch (RestClientResponseException exception) {
                log.warn("Failed to load my roles. status={}, body={}",
                        exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
                myRolesLoadError = "내 권한 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
            } catch (RestClientException | IllegalStateException exception) {
                log.warn("Failed to load my roles.", exception);
                myRolesLoadError = "내 권한 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
            }
        } else {
            myRolesLoadError = "내 권한을 조회할 권한이 없습니다.";
        }

        List<TeamMemberView> teamMembers = List.of();
        String teamMembersLoadError = null;

        if (canViewTeamRoles) {
            try {
                AdminRoleUserListResponse userResponse = adminRoleClient.listAdminUsers();
                teamMembers = Optional.ofNullable(userResponse)
                        .map(AdminRoleUserListResponse::users)
                        .orElseGet(List::of)
                        .stream()
                        .map(this::toTeamMemberView)
                        .sorted(teamMemberComparator())
                        .toList();
            } catch (RestClientResponseException exception) {
                log.warn("Failed to load team members. status={}, body={}",
                        exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
                teamMembersLoadError = "팀원 권한 정보를 불러오지 못했습니다.";
            } catch (RestClientException | IllegalStateException exception) {
                log.warn("Failed to load team members.", exception);
                teamMembersLoadError = "팀원 권한 정보를 불러오지 못했습니다.";
            }
        } else {
            teamMembersLoadError = "팀원 권한을 조회할 권한이 없습니다.";
        }

        String normalizedTab = normalizeTab(tab, canViewTeamRoles, canManageRoles);

        model.addAttribute("tab", normalizedTab);
        model.addAttribute("canViewMyRoles", canViewMyRoles);
        model.addAttribute("canViewTeamTab", canViewTeamRoles);
        model.addAttribute("canManageRoles", canManageRoles);
        model.addAttribute("hasRoleStructureAccess", canViewTeamRoles || canManageRoles);

        model.addAttribute("myRoles", myRoles);
        model.addAttribute("myRolePermissionGroups", myRolePermissionGroups);
        model.addAttribute("myRolesLoadError", myRolesLoadError);

        model.addAttribute("teamMembers", teamMembers);
        model.addAttribute("teamMemberCount", teamMembers.size());
        model.addAttribute("teamMembersLoadError", teamMembersLoadError);
        model.addAttribute("teamMemberRoleGroupsById", buildTeamMemberRoleGroups(teamMembers, roleTemplatesByCode));

        model.addAttribute("roleTemplates", roleTemplates);
        model.addAttribute("roleTemplateCount", roleTemplates.size());
        model.addAttribute("selectedRoleTemplate", selectedRoleTemplate);
        model.addAttribute("selectedRoleCode", normalizedRoleCode);
        model.addAttribute("roleTemplatesLoadError", roleTemplatesLoadError);
        model.addAttribute("permissionOptions", permissionOptions);

        return "admin-role/index";
    }

    @GetMapping("/team-members/{userId}/edit")
    public String editTeamMember(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                                 @PathVariable Long userId,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        boolean canViewTeamRoles = currentAdminProfile.hasPermission(PERMISSION_ADMIN_ROLE_READ);
        boolean canManageRoles = currentAdminProfile.hasPermission(PERMISSION_ADMIN_ROLE_WRITE);

        if (!canViewTeamRoles) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "팀원 권한을 조회할 권한이 없습니다.");
            return redirectToTeamTab();
        }

        List<RoleTemplateView> roleTemplates = List.of();
        Map<String, RoleTemplateView> roleTemplatesByCode = Map.of();
        String roleTemplatesLoadError = null;

        try {
            AdminRoleListResponse response = adminRoleClient.listRoles();
            roleTemplates = Optional.ofNullable(response)
                    .map(AdminRoleListResponse::roles)
                    .orElseGet(List::of)
                    .stream()
                    .map(this::toRoleTemplateView)
                    .sorted(roleTemplateComparator())
                    .toList();
            roleTemplatesByCode = roleTemplates.stream()
                    .collect(Collectors.toMap(
                            RoleTemplateView::code,
                            template -> template,
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
        } catch (RestClientResponseException exception) {
            log.warn("Failed to load role templates for team member edit. status={}, body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            roleTemplatesLoadError = "권한 템플릿을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load role templates for team member edit.", exception);
            roleTemplatesLoadError = "권한 템플릿을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.";
        }

        TeamMemberView targetMember = null;
        String teamMemberLoadError = null;

        try {
            AdminRoleUserListResponse response = adminRoleClient.listAdminUsers();
            targetMember = Optional.ofNullable(response)
                    .map(AdminRoleUserListResponse::users)
                    .orElseGet(List::of)
                    .stream()
                    .map(this::toTeamMemberView)
                    .filter(member -> Objects.equals(member.id(), userId))
                    .findFirst()
                    .orElse(null);
            if (targetMember == null) {
                teamMemberLoadError = "선택한 팀원을 찾을 수 없습니다.";
            }
        } catch (RestClientResponseException exception) {
            log.warn("Failed to load team member. status={}, body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            teamMemberLoadError = "팀원 정보를 불러오지 못했습니다.";
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to load team member.", exception);
            teamMemberLoadError = "팀원 정보를 불러오지 못했습니다.";
        }

        if (targetMember == null) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", teamMemberLoadError != null
                    ? teamMemberLoadError
                    : "선택한 팀원을 찾을 수 없습니다.");
            return redirectToTeamTab();
        }

        List<RoleDisplay> assignableRoles = roleTemplates.stream()
                .map(RoleTemplateView::role)
                .toList();
        boolean teamRoleEditorAvailable = canManageRoles && !assignableRoles.isEmpty();

        List<RolePermissionGroupView> memberRoleGroups = buildRolePermissionGroups(targetMember, roleTemplatesByCode);

        model.addAttribute("pageTitle", targetMember.nickname() + " 권한 수정");
        model.addAttribute("activeMenu", "adminRoles");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.of("운영 권한", "/admin/roles?tab=" + TAB_TEAM),
                Breadcrumb.active(targetMember.nickname() + " 권한 수정")
        ));
        model.addAttribute("toastMessages", List.of());

        model.addAttribute("teamMember", targetMember);
        model.addAttribute("teamMemberRoleGroups", memberRoleGroups);
        model.addAttribute("assignableRoles", assignableRoles);
        model.addAttribute("teamRoleEditorAvailable", teamRoleEditorAvailable);
        model.addAttribute("roleTemplatesLoadError", roleTemplatesLoadError);
        model.addAttribute("canManageRoles", canManageRoles);

        return "admin-role/team-member-edit";
    }

    @PostMapping("/team-members/{userId}/roles")
    public String updateTeamMemberRoles(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                                        @PathVariable Long userId,
                                        @RequestParam(name = "roleCodes", required = false) List<String> roleCodes,
                                        RedirectAttributes redirectAttributes) {
        if (!currentAdminProfile.hasPermission(PERMISSION_ADMIN_ROLE_WRITE)) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "권한을 편집할 권한이 없습니다.");
            return redirectToTeamMemberEditor(userId);
        }

        Set<String> desiredRoles = new LinkedHashSet<>(normalizeRoleCodes(roleCodes));

        try {
            AdminRoleUserListResponse response = adminRoleClient.listAdminUsers();
            Optional<AdminUserRoleResponse> targetUser = Optional.ofNullable(response)
                    .map(AdminRoleUserListResponse::users)
                    .orElseGet(List::of)
                    .stream()
                    .filter(user -> Objects.equals(user.userId(), userId))
                    .findFirst();
            if (targetUser.isEmpty()) {
                redirectAttributes.addFlashAttribute("flashStatus", "error");
                redirectAttributes.addFlashAttribute("flashMessage", "선택한 팀원을 찾을 수 없습니다.");
                return redirectToTeamMemberEditor(userId);
            }

            Set<String> currentRoles = Optional.of(targetUser.get())
                    .map(AdminUserRoleResponse::roles)
                    .orElseGet(List::of)
                    .stream()
                    .map(RoleSummaryResponse::code)
                    .map(this::normalizeRoleCode)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            Set<String> toGrant = new LinkedHashSet<>(desiredRoles);
            toGrant.removeAll(currentRoles);
            Set<String> toRevoke = new LinkedHashSet<>(currentRoles);
            toRevoke.removeAll(desiredRoles);

            for (String code : toGrant) {
                adminRoleClient.grantRole(userId, new AssignRoleRequest(code));
            }
            for (String code : toRevoke) {
                adminRoleClient.revokeRole(userId, code);
            }

            String message = toGrant.isEmpty() && toRevoke.isEmpty()
                    ? "변경 사항이 없어 기존 구성을 유지했습니다."
                    : "팀원 권한 구성을 업데이트했습니다.";
            redirectAttributes.addFlashAttribute("flashStatus", "success");
            redirectAttributes.addFlashAttribute("flashMessage", message);
        } catch (RestClientResponseException exception) {
            log.warn("Failed to update team member roles. status={}, body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "팀원 권한을 수정하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to update team member roles.", exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "팀원 권한을 수정하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }

        return redirectToTeamMemberEditor(userId);
    }

    @PostMapping("/new")
    public String createRole(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                             @RequestParam String code,
                             @RequestParam String displayName,
                             @RequestParam String description,
                             @RequestParam(name = "permissions", required = false) List<String> permissions,
                             RedirectAttributes redirectAttributes) {
        if (!currentAdminProfile.hasPermission(PERMISSION_ADMIN_ROLE_WRITE)) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "권한을 생성할 권한이 없습니다.");
            return redirectToManage(null);
        }

        String normalizedCode = normalizeRoleCode(code);
        String normalizedDisplayName = StringUtils.hasText(displayName) ? displayName.trim() : "";
        String normalizedDescription = StringUtils.hasText(description) ? description.trim() : "";

        if (!StringUtils.hasText(normalizedCode) || !StringUtils.hasText(normalizedDisplayName)
                || !StringUtils.hasText(normalizedDescription)) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "권한 코드, 이름, 설명을 모두 입력해 주세요.");
            return redirectToManage(normalizedCode);
        }

        try {
            adminRoleClient.createRole(new CreateRoleRequest(normalizedCode, normalizedDisplayName, normalizedDescription));
            List<String> permissionKeys = normalizePermissionKeys(permissions);
            if (!permissionKeys.isEmpty()) {
                adminRoleClient.updateRolePermissions(normalizedCode, new UpdateRolePermissionsRequest(permissionKeys));
            }
            redirectAttributes.addFlashAttribute("flashStatus", "success");
            redirectAttributes.addFlashAttribute("flashMessage", "새 권한을 생성했습니다.");
        } catch (RestClientResponseException exception) {
            log.warn("Failed to create role. status={}, body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "새 권한을 생성하지 못했습니다. 입력값을 확인해 주세요.");
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to create role.", exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "새 권한을 생성하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }

        return redirectToManage(normalizedCode);
    }

    @PostMapping("/{roleCode}/permissions")
    public String updateRolePermissions(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                                        @PathVariable String roleCode,
                                        @RequestParam(name = "permissions", required = false) List<String> permissions,
                                        RedirectAttributes redirectAttributes) {
        if (!currentAdminProfile.hasPermission(PERMISSION_ADMIN_ROLE_WRITE)) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "권한을 편집할 권한이 없습니다.");
            return redirectToManage(roleCode);
        }

        String normalizedCode = normalizeRoleCode(roleCode);
        List<String> permissionKeys = normalizePermissionKeys(permissions);

        try {
            adminRoleClient.updateRolePermissions(normalizedCode, new UpdateRolePermissionsRequest(permissionKeys));
            redirectAttributes.addFlashAttribute("flashStatus", "success");
            redirectAttributes.addFlashAttribute("flashMessage", "권한 구성을 업데이트했습니다.");
        } catch (RestClientResponseException exception) {
            log.warn("Failed to update role permissions. status={}, body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "권한 세부 사항을 업데이트하지 못했습니다.");
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to update role permissions.", exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "권한 세부 사항을 업데이트하지 못했습니다.");
        }

        return redirectToManage(normalizedCode);
    }

    @PostMapping("/{roleCode}/delete")
    public String deleteRole(@ModelAttribute("currentAdminProfile") CurrentAdminProfile currentAdminProfile,
                             @PathVariable String roleCode,
                             RedirectAttributes redirectAttributes) {
        if (!currentAdminProfile.hasPermission(PERMISSION_ADMIN_ROLE_WRITE)) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "권한을 삭제할 권한이 없습니다.");
            return redirectToManage(roleCode);
        }

        String normalizedCode = normalizeRoleCode(roleCode);
        if (!StringUtils.hasText(normalizedCode)) {
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "삭제할 권한 코드를 확인해 주세요.");
            return redirectToManage(null);
        }

        try {
            adminRoleClient.deleteRole(normalizedCode);
            redirectAttributes.addFlashAttribute("flashStatus", "success");
            redirectAttributes.addFlashAttribute("flashMessage", "권한을 삭제했습니다.");
        } catch (RestClientResponseException exception) {
            log.warn("Failed to delete role. status={}, body={}",
                    exception.getStatusCode(), exception.getResponseBodyAsString(), exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "권한을 삭제하지 못했습니다.");
        } catch (RestClientException | IllegalStateException exception) {
            log.warn("Failed to delete role.", exception);
            redirectAttributes.addFlashAttribute("flashStatus", "error");
            redirectAttributes.addFlashAttribute("flashMessage", "권한을 삭제하지 못했습니다.");
        }

        return redirectToManage(null);
    }

    private Comparator<RoleTemplateView> roleTemplateComparator() {
        return Comparator
                .comparing(RoleTemplateView::displayName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(RoleTemplateView::code, String.CASE_INSENSITIVE_ORDER);
    }

    private Comparator<TeamMemberView> teamMemberComparator() {
        return Comparator
                .comparing(TeamMemberView::nickname, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(TeamMemberView::id, Comparator.nullsLast(Long::compareTo));
    }

    private List<PermissionView> resolvePermissions(String roleCode, Map<String, RoleTemplateView> mapping) {
        if (!StringUtils.hasText(roleCode) || mapping.isEmpty()) {
            return List.of();
        }
        String normalized = normalizeRoleCode(roleCode);
        RoleTemplateView template = mapping.get(normalized);
        if (template == null || template.permissions() == null) {
            return List.of();
        }
        return template.permissions();
    }

    private RoleTemplateView toRoleTemplateView(RoleDetailResponse response) {
        RoleDisplay display = new RoleDisplay(
                response.id(),
                response.code(),
                response.displayName(),
                response.description(),
                response.builtin()
        );
        List<PermissionView> permissions = Optional.ofNullable(response.permissions())
                .orElseGet(List::of)
                .stream()
                .map(this::toPermissionView)
                .toList();
        return new RoleTemplateView(display, permissions);
    }

    private RoleDisplay toRoleDisplay(RoleSummaryResponse response) {
        if (response == null) {
            return new RoleDisplay(null, "", "", "", false);
        }
        return new RoleDisplay(
                response.id(),
                response.code(),
                response.displayName(),
                response.description(),
                response.builtin()
        );
    }

    private PermissionView toPermissionView(PermissionSummaryResponse response) {
        if (response == null) {
            return new PermissionView("", "");
        }
        return new PermissionView(response.key(), response.description());
    }

    private TeamMemberView toTeamMemberView(AdminUserRoleResponse response) {
        List<RoleDisplay> roles = Optional.ofNullable(response.roles())
                .orElseGet(List::of)
                .stream()
                .map(this::toRoleDisplay)
                .toList();
        List<PermissionView> permissions = Optional.ofNullable(response.permissions())
                .orElseGet(List::of)
                .stream()
                .map(this::toPermissionView)
                .toList();
        return new TeamMemberView(
                response.userId(),
                response.nickname(),
                response.email(),
                response.superAdmin(),
                roles,
                permissions
        );
    }

    private List<PermissionOptionView> buildPermissionOptions(List<RoleTemplateView> templates) {
        Map<String, PermissionOptionView> deduplicated = new LinkedHashMap<>();
        for (RoleTemplateView template : templates) {
            for (PermissionView permission : template.permissions()) {
                deduplicated.put(permission.key(), new PermissionOptionView(permission.key(), permission.description()));
            }
        }
        for (PermissionOptionView builtin : PermissionCatalog.builtinPermissions()) {
            deduplicated.putIfAbsent(builtin.key(), builtin);
        }
        Comparator<PermissionOptionView> comparator = Comparator
                .comparing(PermissionOptionView::label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(PermissionOptionView::key, String.CASE_INSENSITIVE_ORDER);
        return deduplicated.values().stream().sorted(comparator).toList();
    }

    private String normalizeTab(String requestedTab, boolean canViewTeamRoles, boolean canManageRoles) {
        List<String> order = new ArrayList<>();
        order.add(TAB_MY);
        if (canViewTeamRoles) {
            order.add(TAB_TEAM);
        }
        if (canManageRoles) {
            order.add(TAB_MANAGE);
        }
        String normalized = StringUtils.hasText(requestedTab) ? requestedTab.trim().toLowerCase(Locale.ROOT) : TAB_MY;
        if (!order.contains(normalized)) {
            normalized = order.get(0);
        }
        return normalized;
    }

    private String normalizeRoleCode(String code) {
        if (!StringUtils.hasText(code)) {
            return "";
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private List<String> normalizeRoleCodes(List<String> rawCodes) {
        if (rawCodes == null || rawCodes.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : rawCodes) {
            String value = normalizeRoleCode(raw);
            if (StringUtils.hasText(value)) {
                normalized.add(value);
            }
        }
        if (normalized.isEmpty()) {
            return List.of();
        }
        return List.copyOf(normalized);
    }

    private List<String> normalizePermissionKeys(List<String> rawKeys) {
        if (rawKeys == null || rawKeys.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String raw : rawKeys) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            normalized.add(raw.trim().toUpperCase(Locale.ROOT));
        }
        if (normalized.isEmpty()) {
            return List.of();
        }
        return List.copyOf(normalized);
    }

    private String redirectToTeamMemberEditor(Long userId) {
        if (userId == null) {
            return redirectToTeamTab();
        }
        return "redirect:/admin/roles/team-members/" + userId + "/edit";
    }

    private String redirectToTeamTab() {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/admin/roles")
                .queryParam("tab", TAB_TEAM);
        return "redirect:" + builder.toUriString();
    }

    private Map<Long, List<RolePermissionGroupView>> buildTeamMemberRoleGroups(List<TeamMemberView> members,
                                                                               Map<String, RoleTemplateView> mapping) {
        if (members.isEmpty()) {
            return Map.of();
        }
        return members.stream()
                .filter(member -> member.id() != null)
                .collect(Collectors.toMap(
                        TeamMemberView::id,
                        member -> buildRolePermissionGroups(member, mapping),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private List<RolePermissionGroupView> buildRolePermissionGroups(TeamMemberView member,
                                                                    Map<String, RoleTemplateView> mapping) {
        if (member == null) {
            return List.of();
        }
        return member.roles().stream()
                .map(role -> new RolePermissionGroupView(role, resolvePermissions(role.code(), mapping)))
                .toList();
    }

    private String redirectToManage(String selectedRoleCode) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/admin/roles")
                .queryParam("tab", TAB_MANAGE);
        if (StringUtils.hasText(selectedRoleCode)) {
            builder.queryParam("selectedRoleCode", normalizeRoleCode(selectedRoleCode));
        }
        return "redirect:" + builder.toUriString();
    }
}
