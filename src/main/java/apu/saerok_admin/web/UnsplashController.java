package apu.saerok_admin.web;

import apu.saerok_admin.infra.unsplash.UnsplashService;
import apu.saerok_admin.infra.unsplash.UnsplashService.Photo;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 없이 접근 가능한 프록시 엔드포인트.
 * - /public/unsplash/random-bird?orientation=landscape|portrait
 * 브라우저에는 키를 절대 노출하지 않고 단일 사진 메타만 반환한다.
 */
@RestController
public class UnsplashController {

    private final UnsplashService unsplashService;

    public UnsplashController(UnsplashService unsplashService) {
        this.unsplashService = unsplashService;
    }

    @GetMapping(path = "/public/unsplash/random-bird", produces = MediaType.APPLICATION_JSON_VALUE)
    public Photo randomBird(@RequestParam(name = "orientation", required = false) String orientation) {
        return unsplashService.fetchRandomBird(orientation);
    }
}
