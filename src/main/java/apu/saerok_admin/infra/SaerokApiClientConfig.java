package apu.saerok_admin.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SaerokApiProps.class)
public class SaerokApiClientConfig {
  @Bean
  RestClient saerokRestClient(SaerokApiProps props) {
    return RestClient.builder().baseUrl(props.baseUrl()).build();
  }
}

@ConfigurationProperties(prefix = "saerok.api")
record SaerokApiProps(String baseUrl) {}
