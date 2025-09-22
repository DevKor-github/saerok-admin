package apu.saerok_admin.infra;

import java.time.Clock;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SaerokApiProps.class)
public class SaerokApiClientConfig {
    @Bean
    RestClient saerokRestClient(SaerokApiProps props) {
        return RestClient.builder().baseUrl(props.baseUrl()).build();
    }

    @Bean
    Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}

@ConfigurationProperties(prefix = "saerok.api")
record SaerokApiProps(String baseUrl) {}
