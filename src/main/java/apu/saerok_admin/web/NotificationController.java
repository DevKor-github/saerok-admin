package apu.saerok_admin.web;

import apu.saerok_admin.web.view.Breadcrumb;
import apu.saerok_admin.web.view.NotificationTargetOption;
import apu.saerok_admin.web.view.ToastMessage;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/notifications")
public class NotificationController {

    @GetMapping("/compose")
    public String compose(Model model) {
        model.addAttribute("pageTitle", "시스템 알림 발송");
        model.addAttribute("activeMenu", "notifications");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.active("시스템 알림")
        ));
        model.addAttribute("toastMessages", List.of(
                ToastMessage.success("toastNotificationPreview", "미리보기 생성", "미리보기가 새 창에서 확인 가능합니다."),
                ToastMessage.success("toastNotificationSent", "발송 완료", "알림 발송 요청이 접수되었습니다.")
        ));
        model.addAttribute("targetOptions", List.of(
                new NotificationTargetOption("ALL", "전체 사용자", "모든 사용자에게 발송"),
                new NotificationTargetOption("ACTIVE", "최근 30일 활동", "최근 30일 내 활동한 사용자"),
                new NotificationTargetOption("PREMIUM", "프리미엄", "프리미엄 구독자 대상"),
                new NotificationTargetOption("CUSTOM", "조건 직접 설정", "세그먼트 규칙으로 지정")
        ));
        return "notifications/compose";
    }
}
