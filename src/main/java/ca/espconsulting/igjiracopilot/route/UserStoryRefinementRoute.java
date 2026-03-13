package ca.espconsulting.igjiracopilot.route;

import ca.espconsulting.igjiracopilot.config.AppProperties;
import ca.espconsulting.igjiracopilot.dto.JiraIssue;
import ca.espconsulting.igjiracopilot.service.CopilotClient;
import ca.espconsulting.igjiracopilot.service.JiraClient;
import ca.espconsulting.igjiracopilot.service.PollingService;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Route 1 – User Story Refinement.
 *
 * <p>Polls Jira periodically for issues where the AI Exchange Tracking field
 * starts with "AI01" (Waiting for refinement).  Each issue is refined by the
 * Copilot AI and its description is updated together with the tracking field.</p>
 *
 * <pre>
 *   AI01 (Waiting for refinement)
 *     → AI02 (Refinement in progress)   [before Copilot call]
 *     → AI03 (Refinement completed)     [after description updated]
 * </pre>
 */
@Component
public class UserStoryRefinementRoute extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(UserStoryRefinementRoute.class);

    private final AppProperties appProperties;
    private final JiraClient jiraClient;
    private final CopilotClient copilotClient;
    private final PollingService pollingService;

    public UserStoryRefinementRoute(AppProperties appProperties,
                                    JiraClient jiraClient,
                                    CopilotClient copilotClient,
                                    PollingService pollingService) {
        this.appProperties = appProperties;
        this.jiraClient = jiraClient;
        this.copilotClient = copilotClient;
        this.pollingService = pollingService;
    }

    @Override
    public void configure() {
        String label = appProperties.getJira().getAiAgentLabel();
        String trackingField = appProperties.getJira().getAiExchangeTrackingField();
        long intervalMs = appProperties.getRoutes().getScanIntervalMs();

        // Global error handler with 3 retries and exponential back-off
        errorHandler(defaultErrorHandler()
                .maximumRedeliveries(3)
                .redeliveryDelay(2000)
                .backOffMultiplier(2)
                .retryAttemptedLogLevel(org.apache.camel.LoggingLevel.WARN)
                .logHandled(true));

        // -----------------------------------------------------------------------
        // Scheduled scanner: query Jira every <intervalMs> ms
        // -----------------------------------------------------------------------
        from("timer:route1-scanner?period=" + intervalMs + "&delay=5000")
                .routeId("route1-scanner")
                .log("Route 1: scanning Jira for AI01 issues...")
                .process(exchange -> {
                    String jql = "labels = \"" + label + "\""
                            + " AND \"" + trackingField + "\" ~ \"" + AiState.PREFIX_AI01 + "*\""
                            + " ORDER BY created ASC";
                    List<JiraIssue> issues = jiraClient.searchIssues(jql);
                    exchange.getIn().setBody(issues);
                })
                .split(body()).parallelProcessing()
                    .to("seda:route1-process?blockWhenFull=true")
                .end()
                .log("Route 1: scan complete.");

        // -----------------------------------------------------------------------
        // Async processor: handle each issue individually via SEDA
        // -----------------------------------------------------------------------
        from("seda:route1-process?concurrentConsumers=5")
                .routeId("route1-process")
                .process(this::processRefinement);
    }

    /**
     * Core processing logic for a single user story refinement.
     */
    private void processRefinement(Exchange exchange) {
        JiraIssue issue = exchange.getIn().getBody(JiraIssue.class);
        if (issue == null) {
            log.warn("Route 1: received null issue, skipping");
            return;
        }

        String key = issue.getKey();
        String summary = issue.getFields() != null ? issue.getFields().getSummary() : "";
        String description = issue.getDescriptionText();

        log.info("Route 1: processing issue {} – {}", key, summary);

        // Mark as "Refinement in progress" before calling AI
        jiraClient.updateAiExchangeTracking(key, AiState.AI02_REFINEMENT_IN_PROGRESS);

        // Build prompt and call Copilot (with polling)
        String prompt = copilotClient.buildRefinementPrompt(summary, description);
        String refinedContent = pollingService.pollUntilResult(
                "refinement of " + key,
                () -> copilotClient.sendPrompt(prompt));

        // Update Jira with the refined story
        jiraClient.updateDescription(key, refinedContent);
        jiraClient.updateAiExchangeTracking(key, AiState.AI03_REFINEMENT_COMPLETED);

        log.info("Route 1: completed refinement for {}", key);
    }
}
