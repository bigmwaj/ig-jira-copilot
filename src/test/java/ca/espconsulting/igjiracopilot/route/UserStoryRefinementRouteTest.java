package ca.espconsulting.igjiracopilot.route;

import ca.espconsulting.igjiracopilot.config.AppProperties;
import ca.espconsulting.igjiracopilot.dto.JiraIssue;
import ca.espconsulting.igjiracopilot.service.CopilotClient;
import ca.espconsulting.igjiracopilot.service.JiraClient;
import ca.espconsulting.igjiracopilot.service.PollingService;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.Callable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@CamelSpringBootTest
@SpringBootTest
@UseAdviceWith
@ActiveProfiles("test")
class UserStoryRefinementRouteTest {

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    @MockBean
    private JiraClient jiraClient;

    @MockBean
    private CopilotClient copilotClient;

    @MockBean
    private PollingService pollingService;

    @EndpointInject("mock:seda:route1-process")
    private MockEndpoint mockSeda;

    @Test
    void shouldSendIssuesToSedaChannel() throws Exception {
        // Arrange
        JiraIssue issue = buildTestIssue("PROJ-1", "Test Story", "AI01 - Waiting for refinement");
        when(jiraClient.searchIssues(anyString())).thenReturn(List.of(issue));

        // Intercept timer and SEDA endpoints
        AdviceWith.adviceWith(camelContext, "route1-scanner", a -> {
            a.replaceFromWith("direct:test-route1-scanner");
            a.weaveByToUri("seda:route1-process*").replace().to("mock:seda:route1-process");
        });

        AdviceWith.adviceWith(camelContext, "route1-process", a ->
                a.replaceFromWith("direct:test-route1-process"));

        camelContext.start();

        mockSeda.expectedMessageCount(1);

        // Act
        producerTemplate.sendBody("direct:test-route1-scanner", null);

        // Assert
        mockSeda.assertIsSatisfied();
    }

    @Test
    void shouldCompleteRefinementWorkflow() throws Exception {
        JiraIssue issue = buildTestIssue("PROJ-2", "Another Story", "AI01 - Waiting for refinement");
        when(copilotClient.buildRefinementPrompt(anyString(), anyString()))
                .thenReturn("refined prompt");
        when(pollingService.pollUntilResult(anyString(), any(Callable.class)))
                .thenReturn("refined content");

        AdviceWith.adviceWith(camelContext, "route1-process", a ->
                a.replaceFromWith("direct:test-route1-process-full"));

        if (!camelContext.isStarted()) {
            camelContext.start();
        }

        producerTemplate.sendBody("direct:test-route1-process-full", issue);

        verify(jiraClient).updateAiExchangeTracking("PROJ-2", AiState.AI02_REFINEMENT_IN_PROGRESS);
    }

    private JiraIssue buildTestIssue(String key, String summary, String aiState) {
        JiraIssue issue = new JiraIssue();
        issue.setId("10001");
        issue.setKey(key);
        JiraIssue.Fields fields = new JiraIssue.Fields();
        fields.setSummary(summary);
        fields.setAiExchangeTracking(aiState);
        issue.setFields(fields);
        return issue;
    }
}
