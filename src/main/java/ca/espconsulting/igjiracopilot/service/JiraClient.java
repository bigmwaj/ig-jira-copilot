package ca.espconsulting.igjiracopilot.service;

import ca.espconsulting.igjiracopilot.config.AppProperties;
import ca.espconsulting.igjiracopilot.dto.JiraIssue;
import ca.espconsulting.igjiracopilot.dto.JiraTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Client service for Jira REST API (v3).
 * Handles issue search, field updates, and task creation.
 */
@Service
public class JiraClient {

    private static final Logger log = LoggerFactory.getLogger(JiraClient.class);

    private final AppProperties appProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public JiraClient(AppProperties appProperties, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Search Jira for issues matching the given JQL query.
     *
     * @param jql the JQL query string
     * @return list of matching JiraIssue objects
     */
    public List<JiraIssue> searchIssues(String jql) {
        List<JiraIssue> issues = new ArrayList<>();
        try {
            String encodedJql = URLEncoder.encode(jql, StandardCharsets.UTF_8);
            String url = appProperties.getJira().getBaseUrl()
                    + "/rest/api/3/search?jql=" + encodedJql
                    + "&fields=summary,description,labels,issuetype,"
                    + appProperties.getJira().getAiExchangeTrackingField();

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(buildHeaders()), JsonNode.class);

            if (response.getBody() != null) {
                JsonNode issuesNode = response.getBody().get("issues");
                if (issuesNode != null && issuesNode.isArray()) {
                    for (JsonNode node : issuesNode) {
                        JiraIssue issue = objectMapper.treeToValue(node, JiraIssue.class);
                        issues.add(issue);
                    }
                }
            }
            log.info("JQL '{}' returned {} issue(s)", jql, issues.size());
        } catch (Exception e) {
            log.error("Error searching Jira with JQL '{}': {}", jql, e.getMessage(), e);
        }
        return issues;
    }

    /**
     * Update the AI Exchange Tracking custom field on a Jira issue.
     *
     * @param issueKey   the Jira issue key (e.g., PROJ-123)
     * @param stateValue the new state value (e.g., AI02)
     */
    public void updateAiExchangeTracking(String issueKey, String stateValue) {
        try {
            String url = appProperties.getJira().getBaseUrl()
                    + "/rest/api/3/issue/" + issueKey;

            ObjectNode fields = objectMapper.createObjectNode();
            fields.put(appProperties.getJira().getAiExchangeTrackingField(), stateValue);

            ObjectNode body = objectMapper.createObjectNode();
            body.set("fields", fields);

            HttpEntity<String> entity = new HttpEntity<>(
                    objectMapper.writeValueAsString(body), buildHeaders());

            restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);
            log.info("Updated AI Exchange Tracking on {} to {}", issueKey, stateValue);
        } catch (Exception e) {
            log.error("Error updating AI Exchange Tracking on {}: {}", issueKey, e.getMessage(), e);
            throw new RuntimeException("Failed to update Jira field on " + issueKey, e);
        }
    }

    /**
     * Update the description of a Jira issue using the Atlassian Document Format (ADF).
     *
     * @param issueKey    the Jira issue key
     * @param description the plain-text description to set
     */
    public void updateDescription(String issueKey, String description) {
        try {
            String url = appProperties.getJira().getBaseUrl()
                    + "/rest/api/3/issue/" + issueKey;

            ObjectNode descNode = buildAdfDescription(description);

            ObjectNode fields = objectMapper.createObjectNode();
            fields.set("description", descNode);

            ObjectNode body = objectMapper.createObjectNode();
            body.set("fields", fields);

            HttpEntity<String> entity = new HttpEntity<>(
                    objectMapper.writeValueAsString(body), buildHeaders());

            restTemplate.exchange(url, HttpMethod.PUT, entity, Void.class);
            log.info("Updated description on {}", issueKey);
        } catch (Exception e) {
            log.error("Error updating description on {}: {}", issueKey, e.getMessage(), e);
            throw new RuntimeException("Failed to update description on " + issueKey, e);
        }
    }

    /**
     * Create a new Jira Task linked to an existing story/task.
     *
     * @param task the task details
     * @return the key of the created task, or null on failure
     */
    public String createTask(JiraTask task) {
        try {
            String url = appProperties.getJira().getBaseUrl() + "/rest/api/3/issue";

            ObjectNode project = objectMapper.createObjectNode();
            project.put("key", task.getProjectKey());

            ObjectNode issuetype = objectMapper.createObjectNode();
            issuetype.put("name", "Task");

            ArrayNode labelsNode = objectMapper.createArrayNode();
            labelsNode.add(appProperties.getJira().getAiAgentLabel());

            ObjectNode fields = objectMapper.createObjectNode();
            fields.set("project", project);
            fields.put("summary", task.getSummary());
            fields.set("description", buildAdfDescription(task.getDescription()));
            fields.set("issuetype", issuetype);
            fields.set("labels", labelsNode);

            ObjectNode body = objectMapper.createObjectNode();
            body.set("fields", fields);

            HttpEntity<String> entity = new HttpEntity<>(
                    objectMapper.writeValueAsString(body), buildHeaders());

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, JsonNode.class);

            String newKey = null;
            if (response.getBody() != null) {
                newKey = response.getBody().get("key").asText();
            }

            if (newKey != null && task.getLinkedIssueKey() != null) {
                linkIssues(newKey, task.getLinkedIssueKey(), task.getLinkType());
            }

            log.info("Created task {} linked to {}", newKey, task.getLinkedIssueKey());
            return newKey;
        } catch (Exception e) {
            log.error("Error creating task: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create Jira task", e);
        }
    }

    /**
     * Create an issue link between two Jira issues.
     */
    private void linkIssues(String inwardKey, String outwardKey, String linkTypeName) {
        try {
            String url = appProperties.getJira().getBaseUrl() + "/rest/api/3/issueLink";

            ObjectNode linkType = objectMapper.createObjectNode();
            linkType.put("name", linkTypeName);

            ObjectNode inward = objectMapper.createObjectNode();
            inward.put("key", inwardKey);

            ObjectNode outward = objectMapper.createObjectNode();
            outward.put("key", outwardKey);

            ObjectNode body = objectMapper.createObjectNode();
            body.set("type", linkType);
            body.set("inwardIssue", inward);
            body.set("outwardIssue", outward);

            HttpEntity<String> entity = new HttpEntity<>(
                    objectMapper.writeValueAsString(body), buildHeaders());

            restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
            log.info("Linked {} -> {} ({})", inwardKey, outwardKey, linkTypeName);
        } catch (Exception e) {
            log.warn("Could not create issue link between {} and {}: {}", inwardKey, outwardKey, e.getMessage());
        }
    }

    /**
     * Build standard Jira API request headers with Basic Auth.
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String credentials = appProperties.getJira().getUsername()
                + ":" + appProperties.getJira().getApiToken();
        String encoded = Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        return headers;
    }

    /**
     * Wrap plain text into Atlassian Document Format (ADF) for Jira REST v3.
     */
    private ObjectNode buildAdfDescription(String text) {
        ObjectNode doc = objectMapper.createObjectNode();
        doc.put("type", "doc");
        doc.put("version", 1);

        ArrayNode content = objectMapper.createArrayNode();
        ObjectNode paragraph = objectMapper.createObjectNode();
        paragraph.put("type", "paragraph");

        ArrayNode inlineContent = objectMapper.createArrayNode();
        ObjectNode textNode = objectMapper.createObjectNode();
        textNode.put("type", "text");
        textNode.put("text", text);
        inlineContent.add(textNode);

        paragraph.set("content", inlineContent);
        content.add(paragraph);
        doc.set("content", content);
        return doc;
    }
}
