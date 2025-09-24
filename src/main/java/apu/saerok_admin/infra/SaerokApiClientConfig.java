package apu.saerok_admin.infra;

import apu.saerok_admin.config.SocialLoginProperties;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({SaerokApiProps.class, SocialLoginProperties.class})
public class SaerokApiClientConfig {

    @Bean
    @Primary
    RestClient saerokRestClient(SaerokApiProps props, ObjectProvider<ClientHttpRequestInterceptor> interceptors) {
        RestClient.Builder builder = RestClient.builder().baseUrl(props.baseUrl());
        interceptors.orderedStream().forEach(builder::requestInterceptor);
        return builder.build();
    }

    @Bean(name = "saerokAuthRestClient")
    RestClient saerokAuthRestClient(SaerokApiProps props) {
        return RestClient.builder().baseUrl(props.baseUrl()).build();
    }

    @Bean
    Clock systemClock() {
        return Clock.systemDefaultZone();
    }
}

@ConfigurationProperties(prefix = "saerok.api")
record SaerokApiProps(String baseUrl) {}
