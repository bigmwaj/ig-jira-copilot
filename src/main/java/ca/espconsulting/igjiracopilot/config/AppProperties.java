package ca.espconsulting.igjiracopilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Centralised configuration properties for the application.
 * All values are externalised via application.yml.
 */
@ConfigurationProperties(prefix = "app")
@Validated
public class AppProperties {

    private Jira jira = new Jira();
    private Copilot copilot = new Copilot();
    private Routes routes = new Routes();

    public Jira getJira() { return jira; }
    public void setJira(Jira jira) { this.jira = jira; }

    public Copilot getCopilot() { return copilot; }
    public void setCopilot(Copilot copilot) { this.copilot = copilot; }

    public Routes getRoutes() { return routes; }
    public void setRoutes(Routes routes) { this.routes = routes; }

    public static class Jira {
        private String baseUrl = "https://your-jira-instance.atlassian.net";
        private String username = "";
        private String apiToken = "";
        private String projectKey = "PROJ";
        private String aiExchangeTrackingField = "customfield_10100";
        private String aiAgentLabel = "AI-Agent";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getApiToken() { return apiToken; }
        public void setApiToken(String apiToken) { this.apiToken = apiToken; }

        public String getProjectKey() { return projectKey; }
        public void setProjectKey(String projectKey) { this.projectKey = projectKey; }

        public String getAiExchangeTrackingField() { return aiExchangeTrackingField; }
        public void setAiExchangeTrackingField(String field) { this.aiExchangeTrackingField = field; }

        public String getAiAgentLabel() { return aiAgentLabel; }
        public void setAiAgentLabel(String aiAgentLabel) { this.aiAgentLabel = aiAgentLabel; }
    }

    public static class Copilot {
        private String apiUrl = "https://api.githubcopilot.com";
        private String apiKey = "";
        private String model = "gpt-4o";
        private int maxPollAttempts = 30;
        private long pollIntervalMs = 5000;

        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public int getMaxPollAttempts() { return maxPollAttempts; }
        public void setMaxPollAttempts(int maxPollAttempts) { this.maxPollAttempts = maxPollAttempts; }

        public long getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
    }

    public static class Routes {
        private long scanIntervalMs = 60000;

        public long getScanIntervalMs() { return scanIntervalMs; }
        public void setScanIntervalMs(long scanIntervalMs) { this.scanIntervalMs = scanIntervalMs; }
    }
}
