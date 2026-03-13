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
 * Route 3 – Development Plan Review.
 *
 * <p>Polls Jira for tasks where the AI Exchange Tracking field starts with "AI07"
 * (Waiting for dev plan review). The AI reviews the current development plan and
 * returns an updated version which overwrites the task description.</p>
 *
 * <pre>
 *   AI07 (Waiting for dev plan review)
 *     → AI08 (Review in progress)
 *     → AI09 (Review completed)  + task description updated
 * </pre>
 */
@Component
public class DevPlanReviewRoute extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(DevPlanReviewRoute.class);

    private final AppProperties appProperties;
    private final JiraClient jiraClient;
    private final CopilotClient copilotClient;
    private final PollingService pollingService;

    public DevPlanReviewRoute(AppProperties appProperties,
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

        errorHandler(defaultErrorHandler()
                .maximumRedeliveries(3)
                .redeliveryDelay(2000)
                .backOffMultiplier(2)
                .retryAttemptedLogLevel(org.apache.camel.LoggingLevel.WARN)
                .logHandled(true));

        from("timer:route3-scanner?period=" + intervalMs + "&delay=15000")
                .routeId("route3-scanner")
                .log("Route 3: scanning Jira for AI07 tasks...")
                .process(exchange -> {
                    String jql = "labels = \"" + label + "\""
                            + " AND \"" + trackingField + "\" ~ \"" + AiState.PREFIX_AI07 + "*\""
                            + " ORDER BY created ASC";
                    List<JiraIssue> issues = jiraClient.searchIssues(jql);
                    exchange.getIn().setBody(issues);
                })
                .split(body()).parallelProcessing()
                    .to("seda:route3-process?blockWhenFull=true")
                .end()
                .log("Route 3: scan complete.");

        from("seda:route3-process?concurrentConsumers=5")
                .routeId("route3-process")
                .process(this::processDevPlanReview);
    }

    private void processDevPlanReview(Exchange exchange) {
        JiraIssue issue = exchange.getIn().getBody(JiraIssue.class);
        if (issue == null) {
            log.warn("Route 3: received null issue, skipping");
            return;
        }

        String key = issue.getKey();
        String summary = issue.getFields() != null ? issue.getFields().getSummary() : "";
        String currentPlan = issue.getDescriptionText();

        log.info("Route 3: reviewing dev plan for task {} – {}", key, summary);

        // Mark review in progress
        jiraClient.updateAiExchangeTracking(key, AiState.AI08_REVIEW_IN_PROGRESS);

        // Review dev plan via AI
        String prompt = copilotClient.buildDevPlanReviewPrompt(summary, currentPlan);
        String reviewedPlan = pollingService.pollUntilResult(
                "dev plan review for " + key,
                () -> copilotClient.sendPrompt(prompt));

        // Update task description with reviewed plan
        jiraClient.updateDescription(key, reviewedPlan);
        jiraClient.updateAiExchangeTracking(key, AiState.AI09_REVIEW_COMPLETED);

        log.info("Route 3: completed dev plan review for {}", key);
    }
}
