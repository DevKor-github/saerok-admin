package apu.saerok_admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Unsplash API 설정 바인딩.
 * application.yml 의 unsplash.* 값을 주입받아 서버에서만 사용한다.
 * - access-key: Public Access Key (절대 브라우저에 노출하지 않음)
 * - app-name  : utm_source 로 사용할 앱 이름
 */
@Configuration
@ConfigurationProperties(prefix = "unsplash")
public class UnsplashProperties {
    private String accessKey;
    private String appName;

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
}
