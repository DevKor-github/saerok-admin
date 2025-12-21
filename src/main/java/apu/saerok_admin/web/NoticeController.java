package apu.saerok_admin.web;

import apu.saerok_admin.web.view.Breadcrumb;
import apu.saerok_admin.web.view.ToastMessage;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/notices")
public class NoticeController {

    @GetMapping
    public String index(Model model) {
        model.addAttribute("pageTitle", "공지사항 관리");
        model.addAttribute("activeMenu", "notices");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.active("공지사항")
        ));
        model.addAttribute("toastMessages", List.of());
        return "notices/index";
    }

    @GetMapping("/new")
    public String compose(Model model) {
        model.addAttribute("pageTitle", "공지사항 작성");
        model.addAttribute("activeMenu", "notices");
        model.addAttribute("breadcrumbs", List.of(
                Breadcrumb.of("대시보드", "/"),
                Breadcrumb.of("공지사항", "/notices"),
                Breadcrumb.active("작성")
        ));
        model.addAttribute("toastMessages", List.of(
                ToastMessage.info("toastNoticePublishPending", "준비 중", "백엔드 연동이 완료되면 공지사항을 게시할 수 있어요."),
                ToastMessage.info("toastNoticeSavedPending", "준비 중", "현재는 저장/게시 API 연동이 되어 있지 않습니다.")
        ));
        return "notices/compose";
    }
}
