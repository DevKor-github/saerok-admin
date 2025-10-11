package apu.saerok_admin.web;

import apu.saerok_admin.infra.unsplash.UnsplashService;
import apu.saerok_admin.infra.unsplash.UnsplashService.Result;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 없이 접근 가능한 프록시 엔드포인트.
 * - /public/unsplash/random-bird
 * 프론트는 이 JSON만 사용하고, 액세스 키는 절대 노출되지 않는다.
 */
@RestController
public class UnsplashController {

    private final UnsplashService unsplashService;

    public UnsplashController(UnsplashService unsplashService) {
        this.unsplashService = unsplashService;
    }

    @GetMapping(path = "/public/unsplash/random-bird", produces = MediaType.APPLICATION_JSON_VALUE)
    public Result randomBird() {
        return unsplashService.fetchRandomBird();
    }
}
