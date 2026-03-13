package ca.espconsulting.igjiracopilot.service;

import ca.espconsulting.igjiracopilot.config.AppProperties;
import ca.espconsulting.igjiracopilot.dto.CopilotRequest;
import ca.espconsulting.igjiracopilot.dto.CopilotResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Client service for the GitHub Copilot / OpenAI-compatible chat completions API.
 */
@Service
public class CopilotClient {

    private static final Logger log = LoggerFactory.getLogger(CopilotClient.class);

    private static final String SYSTEM_PROMPT =
            "You are an expert software architect and developer. "
            + "Provide clear, structured, and actionable responses. "
            + "Use Markdown formatting where appropriate.";

    private final AppProperties appProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public CopilotClient(AppProperties appProperties,
                         RestTemplate restTemplate,
                         ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Send a prompt to the Copilot API and return the full completion response.
     *
     * @param prompt the user prompt to send
     * @return the AI-generated response text
     * @throws RuntimeException if the API call fails
     */
    public String sendPrompt(String prompt) {
        try {
            CopilotRequest request = new CopilotRequest(
                    appProperties.getCopilot().getModel(),
                    SYSTEM_PROMPT,
                    prompt);

            HttpEntity<String> entity = new HttpEntity<>(
                    objectMapper.writeValueAsString(request), buildHeaders());

            String url = appProperties.getCopilot().getApiUrl() + "/chat/completions";
            log.debug("Sending request to Copilot API: {}", url);

            ResponseEntity<CopilotResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, CopilotResponse.class);

            if (response.getBody() == null) {
                throw new RuntimeException("Copilot API returned empty response");
            }

            String content = response.getBody().getFirstChoiceContent();
            log.debug("Received Copilot response ({} chars)", content.length());
            return content;
        } catch (Exception e) {
            log.error("Error calling Copilot API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to call Copilot API", e);
        }
    }

    /**
     * Build a prompt to refine a user story.
     */
    public String buildRefinementPrompt(String summary, String description) {
        return "Refine the following Jira user story to make it clear, complete, and actionable.\n\n"
                + "**Summary:** " + summary + "\n\n"
                + "**Current Description:**\n" + description + "\n\n"
                + "Please provide:\n"
                + "1. A refined description with acceptance criteria\n"
                + "2. Any assumptions made\n"
                + "3. Suggested story points estimate\n";
    }

    /**
     * Build a prompt to generate a development plan from a user story.
     */
    public String buildDevPlanPrompt(String summary, String description) {
        return "Generate a detailed development plan for the following Jira user story.\n\n"
                + "**Summary:** " + summary + "\n\n"
                + "**Description:**\n" + description + "\n\n"
                + "Please provide:\n"
                + "1. Technical approach and architecture\n"
                + "2. Implementation steps (ordered)\n"
                + "3. Required technologies and dependencies\n"
                + "4. Estimated effort per step\n"
                + "5. Testing strategy\n";
    }

    /**
     * Build a prompt to review a development plan.
     */
    public String buildDevPlanReviewPrompt(String summary, String devPlan) {
        return "Review the following development plan for a Jira task and provide feedback.\n\n"
                + "**Task Summary:** " + summary + "\n\n"
                + "**Development Plan:**\n" + devPlan + "\n\n"
                + "Please provide:\n"
                + "1. Assessment of completeness and correctness\n"
                + "2. Identified risks or gaps\n"
                + "3. Suggested improvements\n"
                + "4. Updated/refined development plan incorporating your review\n";
    }

    /**
     * Build a prompt to generate code from a development plan.
     */
    public String buildCodeGenerationPrompt(String summary, String devPlan) {
        return "Generate production-ready code based on the following development plan.\n\n"
                + "**Task Summary:** " + summary + "\n\n"
                + "**Development Plan:**\n" + devPlan + "\n\n"
                + "Please provide:\n"
                + "1. Well-structured, clean, and commented code\n"
                + "2. Unit tests for key components\n"
                + "3. Any configuration or dependency changes required\n";
    }

    /**
     * Build Copilot API request headers.
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(appProperties.getCopilot().getApiKey());
        headers.set("Copilot-Integration-Id", "ig-jira-copilot");
        return headers;
    }
}
