package apu.saerok_admin.web.view;

import java.time.LocalDateTime;
import java.util.List;

public record UserDetail(long id, String nickname, String email, String phone, String status,
                         LocalDateTime joinedAt, LocalDateTime lastLoginAt, int postCount,
                         int reportCount, List<UserActivity> recentActivities) {
}
