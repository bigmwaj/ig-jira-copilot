package ca.espconsulting.igjiracopilot.route;

import ca.espconsulting.igjiracopilot.config.AppProperties;
import ca.espconsulting.igjiracopilot.dto.JiraIssue;
import ca.espconsulting.igjiracopilot.dto.JiraTask;
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
 * Route 2 – Development Plan Generation.
 *
 * <p>Polls Jira for issues where the AI Exchange Tracking field starts with "AI04"
 * (Waiting for development plan).  Calls the AI to generate a development plan,
 * then creates a linked Jira Task with the plan as its description.</p>
 *
 * <pre>
 *   AI04 (Waiting for development plan)
 *     → AI05 (Dev plan generation in progress)
 *     → AI06 (Dev plan generated)  + new linked Task created
 * </pre>
 */
@Component
public class DevPlanGenerationRoute extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(DevPlanGenerationRoute.class);

    private final AppProperties appProperties;
    private final JiraClient jiraClient;
    private final CopilotClient copilotClient;
    private final PollingService pollingService;

    public DevPlanGenerationRoute(AppProperties appProperties,
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

        from("timer:route2-scanner?period=" + intervalMs + "&delay=10000")
                .routeId("route2-scanner")
                .log("Route 2: scanning Jira for AI04 issues...")
                .process(exchange -> {
                    String jql = "labels = \"" + label + "\""
                            + " AND \"" + trackingField + "\" ~ \"" + AiState.PREFIX_AI04 + "*\""
                            + " ORDER BY created ASC";
                    List<JiraIssue> issues = jiraClient.searchIssues(jql);
                    exchange.getIn().setBody(issues);
                })
                .split(body()).parallelProcessing()
                    .to("seda:route2-process?blockWhenFull=true")
                .end()
                .log("Route 2: scan complete.");

        from("seda:route2-process?concurrentConsumers=5")
                .routeId("route2-process")
                .process(this::processDevPlanGeneration);
    }

    private void processDevPlanGeneration(Exchange exchange) {
        JiraIssue issue = exchange.getIn().getBody(JiraIssue.class);
        if (issue == null) {
            log.warn("Route 2: received null issue, skipping");
            return;
        }

        String key = issue.getKey();
        String summary = issue.getFields() != null ? issue.getFields().getSummary() : "";
        String description = issue.getDescriptionText();
        String projectKey = appProperties.getJira().getProjectKey();

        log.info("Route 2: generating dev plan for {} – {}", key, summary);

        // Mark in progress
        jiraClient.updateAiExchangeTracking(key, AiState.AI05_DEV_PLAN_IN_PROGRESS);

        // Generate dev plan via AI
        String prompt = copilotClient.buildDevPlanPrompt(summary, description);
        String devPlan = pollingService.pollUntilResult(
                "dev plan for " + key,
                () -> copilotClient.sendPrompt(prompt));

        // Update parent story tracking field
        jiraClient.updateAiExchangeTracking(key, AiState.AI06_DEV_PLAN_GENERATED);

        // Create a linked task with the dev plan as its description
        JiraTask task = new JiraTask(
                projectKey,
                "Dev Plan: " + summary,
                devPlan,
                key);
        String taskKey = jiraClient.createTask(task);

        log.info("Route 2: created task {} with dev plan for {}", taskKey, key);
    }
}
