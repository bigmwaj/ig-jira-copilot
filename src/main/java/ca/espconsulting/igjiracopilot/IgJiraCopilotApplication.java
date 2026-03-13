package ca.espconsulting.igjiracopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import ca.espconsulting.igjiracopilot.config.AppProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class IgJiraCopilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(IgJiraCopilotApplication.class, args);
    }
}
