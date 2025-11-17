package apu.saerok_admin.web.view.role;

import java.util.List;

public final class PermissionCatalog {

    private static final List<PermissionOptionView> BUILTIN_PERMISSIONS = List.of(
            option("ADMIN_LOGIN", "어드민에 로그인"),
            option("ADMIN_REPORT_READ", "신고된 콘텐츠 내용 조회"),
            option("ADMIN_REPORT_WRITE", "신고된 콘텐츠에 대한 모든 조치"),
            option("ADMIN_AUDIT_READ", "관리자 활동 로그 조회"),
            option("ADMIN_STAT_READ", "서비스 통계 조회"),
            option("ADMIN_STAT_WRITE", "서비스 통계 수동 집계"),
            option("ADMIN_AD_READ", "광고, 광고 위치, 광고 스케줄 조회"),
            option("ADMIN_AD_WRITE", "광고, 광고 위치, 광고 스케줄 생성/수정/삭제 (단, 광고 위치 삭제는 불가)"),
            option("ADMIN_SLOT_DELETE", "광고 위치 삭제"),
            option("ADMIN_ROLE_MY_READ", "로그인한 관리자의 역할/권한 조회"),
            option("ADMIN_ROLE_READ", "모든 관리자(TEAM_MEMBER 기준)의 역할과 권한 조회"),
            option("ADMIN_ROLE_WRITE", "역할 생성/삭제, 권한 편집 및 사용자 역할 부여/회수")
    );

    private PermissionCatalog() {
    }

    public static List<PermissionOptionView> builtinPermissions() {
        return BUILTIN_PERMISSIONS;
    }

    private static PermissionOptionView option(String key, String description) {
        return new PermissionOptionView(key, description);
    }
}
