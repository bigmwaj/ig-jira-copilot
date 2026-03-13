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
 * Route 4 – Code Generation.
 *
 * <p>Polls Jira for tasks where the AI Exchange Tracking field starts with "AI10"
 * (Waiting for code generation). The AI generates production-ready code based on
 * the task's development plan (description).</p>
 *
 * <pre>
 *   AI10 (Waiting for code generation)
 *     → AI11 (Code generation in progress)
 *     → AI12 (Code generation completed)  + task description updated with generated code
 * </pre>
 */
@Component
public class CodeGenerationRoute extends RouteBuilder {

    private static final Logger log = LoggerFactory.getLogger(CodeGenerationRoute.class);

    private final AppProperties appProperties;
    private final JiraClient jiraClient;
    private final CopilotClient copilotClient;
    private final PollingService pollingService;

    public CodeGenerationRoute(AppProperties appProperties,
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

        from("timer:route4-scanner?period=" + intervalMs + "&delay=20000")
                .routeId("route4-scanner")
                .log("Route 4: scanning Jira for AI10 tasks...")
                .process(exchange -> {
                    String jql = "labels = \"" + label + "\""
                            + " AND \"" + trackingField + "\" ~ \"" + AiState.PREFIX_AI10 + "*\""
                            + " ORDER BY created ASC";
                    List<JiraIssue> issues = jiraClient.searchIssues(jql);
                    exchange.getIn().setBody(issues);
                })
                .split(body()).parallelProcessing()
                    .to("seda:route4-process?blockWhenFull=true")
                .end()
                .log("Route 4: scan complete.");

        from("seda:route4-process?concurrentConsumers=5")
                .routeId("route4-process")
                .process(this::processCodeGeneration);
    }

    private void processCodeGeneration(Exchange exchange) {
        JiraIssue issue = exchange.getIn().getBody(JiraIssue.class);
        if (issue == null) {
            log.warn("Route 4: received null issue, skipping");
            return;
        }

        String key = issue.getKey();
        String summary = issue.getFields() != null ? issue.getFields().getSummary() : "";
        String devPlan = issue.getDescriptionText();

        log.info("Route 4: generating code for task {} – {}", key, summary);

        // Mark code generation in progress
        jiraClient.updateAiExchangeTracking(key, AiState.AI11_CODE_GEN_IN_PROGRESS);

        // Generate code via AI
        String prompt = copilotClient.buildCodeGenerationPrompt(summary, devPlan);
        String generatedCode = pollingService.pollUntilResult(
                "code generation for " + key,
                () -> copilotClient.sendPrompt(prompt));

        // Update task description with generated code
        jiraClient.updateDescription(key, generatedCode);
        jiraClient.updateAiExchangeTracking(key, AiState.AI12_CODE_GEN_COMPLETED);

        log.info("Route 4: completed code generation for {}", key);
    }
}
