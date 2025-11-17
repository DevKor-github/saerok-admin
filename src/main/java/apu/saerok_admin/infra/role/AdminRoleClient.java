package apu.saerok_admin.infra.role;

import apu.saerok_admin.infra.SaerokApiProps;
import apu.saerok_admin.infra.role.dto.AdminMyRoleResponse;
import apu.saerok_admin.infra.role.dto.AdminRoleListResponse;
import apu.saerok_admin.infra.role.dto.AdminRoleUserListResponse;
import apu.saerok_admin.infra.role.dto.AdminUserRoleResponse;
import apu.saerok_admin.infra.role.dto.AssignRoleRequest;
import apu.saerok_admin.infra.role.dto.CreateRoleRequest;
import apu.saerok_admin.infra.role.dto.RoleDetailResponse;
import apu.saerok_admin.infra.role.dto.UpdateRolePermissionsRequest;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

@Component
public class AdminRoleClient {

    private static final String[] ADMIN_ROLE_SEGMENTS = {"admin", "role"};

    private final RestClient saerokRestClient;
    private final String[] missingPrefixSegments;

    public AdminRoleClient(RestClient saerokRestClient, SaerokApiProps saerokApiProps) {
        this.saerokRestClient = saerokRestClient;
        List<String> missing = saerokApiProps.missingPrefixSegments();
        this.missingPrefixSegments = missing.toArray(new String[0]);
    }

    public AdminMyRoleResponse getMyRoles() {
        return get(AdminMyRoleResponse.class, "me");
    }

    public AdminRoleUserListResponse listAdminUsers() {
        return get(AdminRoleUserListResponse.class, "users");
    }

    public AdminRoleListResponse listRoles() {
        return get(AdminRoleListResponse.class);
    }

    public RoleDetailResponse createRole(CreateRoleRequest request) {
        return post(RoleDetailResponse.class, request);
    }

    public RoleDetailResponse updateRolePermissions(String roleCode, UpdateRolePermissionsRequest request) {
        return put(RoleDetailResponse.class, request, roleCode, "permissions");
    }

    public void deleteRole(String roleCode) {
        deleteVoid(roleCode);
    }

    public AdminUserRoleResponse grantRole(Long userId, AssignRoleRequest request) {
        return post(AdminUserRoleResponse.class, request, "users", userId.toString(), "roles");
    }

    public AdminUserRoleResponse revokeRole(Long userId, String roleCode) {
        return delete(AdminUserRoleResponse.class, "users", userId.toString(), "roles", roleCode);
    }

    private <T> T get(Class<T> responseType, String... segments) {
        T response = saerokRestClient.get()
                .uri(uriBuilder -> buildUri(uriBuilder, segments))
                .retrieve()
                .body(responseType);
        if (response == null) {
            throw new IllegalStateException("Empty response from admin role API");
        }
        return response;
    }

    private <T> T post(Class<T> responseType, Object body, String... segments) {
        T response = saerokRestClient.post()
                .uri(uriBuilder -> buildUri(uriBuilder, segments))
                .body(body)
                .retrieve()
                .body(responseType);
        if (response == null) {
            throw new IllegalStateException("Empty response from admin role API");
        }
        return response;
    }

    private <T> T put(Class<T> responseType, Object body, String... segments) {
        T response = saerokRestClient.method(HttpMethod.PUT)
                .uri(uriBuilder -> buildUri(uriBuilder, segments))
                .body(body)
                .retrieve()
                .body(responseType);
        if (response == null) {
            throw new IllegalStateException("Empty response from admin role API");
        }
        return response;
    }

    private <T> T delete(Class<T> responseType, String... segments) {
        T response = saerokRestClient.delete()
                .uri(uriBuilder -> buildUri(uriBuilder, segments))
                .retrieve()
                .body(responseType);
        if (response == null) {
            throw new IllegalStateException("Empty response from admin role API");
        }
        return response;
    }

    private void deleteVoid(String... segments) {
        saerokRestClient.delete()
                .uri(uriBuilder -> buildUri(uriBuilder, segments))
                .retrieve()
                .toBodilessEntity();
    }

    private URI buildUri(UriBuilder builder, String... segments) {
        if (missingPrefixSegments.length > 0) {
            builder.pathSegment(missingPrefixSegments);
        }
        builder.pathSegment(ADMIN_ROLE_SEGMENTS);
        if (segments.length > 0) {
            builder.pathSegment(segments);
        }
        return builder.build();
    }
}
