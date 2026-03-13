package ca.espconsulting.igjiracopilot.service;

import ca.espconsulting.igjiracopilot.config.AppProperties;
import ca.espconsulting.igjiracopilot.dto.JiraIssue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PUT;

@ExtendWith(MockitoExtension.class)
class JiraClientTest {

    @Mock
    private RestTemplate restTemplate;

    private JiraClient jiraClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        AppProperties props = new AppProperties();
        props.getJira().setBaseUrl("https://test.atlassian.net");
        props.getJira().setUsername("user@test.com");
        props.getJira().setApiToken("token123");
        props.getJira().setAiExchangeTrackingField("customfield_10100");
        props.getJira().setAiAgentLabel("AI-Agent");
        jiraClient = new JiraClient(props, restTemplate, objectMapper);
    }

    @Test
    void searchIssuesShouldReturnParsedIssues() throws Exception {
        ObjectNode issueNode = objectMapper.createObjectNode();
        issueNode.put("id", "10001");
        issueNode.put("key", "PROJ-1");

        ObjectNode fields = objectMapper.createObjectNode();
        fields.put("summary", "Test Story");
        issueNode.set("fields", fields);

        ArrayNode issuesArray = objectMapper.createArrayNode();
        issuesArray.add(issueNode);

        ObjectNode responseNode = objectMapper.createObjectNode();
        responseNode.set("issues", issuesArray);

        when(restTemplate.exchange(anyString(), eq(GET), any(), eq(JsonNode.class)))
                .thenReturn(new ResponseEntity<>(responseNode, HttpStatus.OK));

        List<JiraIssue> issues = jiraClient.searchIssues("labels = \"AI-Agent\"");

        assertEquals(1, issues.size());
        assertEquals("PROJ-1", issues.get(0).getKey());
        assertEquals("Test Story", issues.get(0).getFields().getSummary());
    }

    @Test
    void searchIssuesShouldReturnEmptyListOnError() {
        when(restTemplate.exchange(anyString(), eq(GET), any(), eq(JsonNode.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        List<JiraIssue> issues = jiraClient.searchIssues("labels = \"AI-Agent\"");

        assertTrue(issues.isEmpty());
    }

    @Test
    void updateAiExchangeTrackingShouldCallPutEndpoint() throws Exception {
        when(restTemplate.exchange(anyString(), eq(PUT), any(), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.NO_CONTENT));

        jiraClient.updateAiExchangeTracking("PROJ-1", "AI02 - Refinement in progress");

        verify(restTemplate).exchange(
                eq("https://test.atlassian.net/rest/api/3/issue/PROJ-1"),
                eq(PUT),
                any(),
                eq(Void.class));
    }

    @Test
    void getDescriptionTextShouldReturnEmptyForNullFields() {
        JiraIssue issue = new JiraIssue();
        assertEquals("", issue.getDescriptionText());
    }
}
