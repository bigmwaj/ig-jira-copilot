package ca.espconsulting.igjiracopilot.service;

import ca.espconsulting.igjiracopilot.config.AppProperties;
import ca.espconsulting.igjiracopilot.dto.CopilotResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;

@ExtendWith(MockitoExtension.class)
class CopilotClientTest {

    @Mock
    private RestTemplate restTemplate;

    private CopilotClient copilotClient;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getCopilot().setApiUrl("https://api.test.com");
        props.getCopilot().setApiKey("test-key");
        props.getCopilot().setModel("gpt-4o");
        copilotClient = new CopilotClient(props, restTemplate, new ObjectMapper());
    }

    @Test
    void shouldReturnAiResponse() {
        CopilotResponse mockResponse = buildMockResponse("AI generated content");
        when(restTemplate.exchange(
                eq("https://api.test.com/chat/completions"),
                eq(POST),
                any(),
                eq(CopilotResponse.class)))
                .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        String result = copilotClient.sendPrompt("Test prompt");
        assertEquals("AI generated content", result);
    }

    @Test
    void shouldThrowWhenApiReturnsNull() {
        when(restTemplate.exchange(any(String.class), eq(POST), any(), eq(CopilotResponse.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

        assertThrows(RuntimeException.class, () -> copilotClient.sendPrompt("Test prompt"));
    }

    @Test
    void buildRefinementPromptContainsKeyTerms() {
        String prompt = copilotClient.buildRefinementPrompt("My Story", "Some description");
        assertTrue(prompt.contains("My Story"));
        assertTrue(prompt.contains("Some description"));
        assertTrue(prompt.contains("acceptance criteria"));
    }

    @Test
    void buildDevPlanPromptContainsKeyTerms() {
        String prompt = copilotClient.buildDevPlanPrompt("My Story", "Some description");
        assertTrue(prompt.contains("development plan"));
        assertTrue(prompt.contains("My Story"));
    }

    @Test
    void buildCodeGenerationPromptContainsKeyTerms() {
        String prompt = copilotClient.buildCodeGenerationPrompt("My Task", "My dev plan");
        assertTrue(prompt.contains("code"));
        assertTrue(prompt.contains("My Task"));
    }

    private CopilotResponse buildMockResponse(String content) {
        CopilotResponse response = new CopilotResponse();
        CopilotResponse.Message message = new CopilotResponse.Message();
        message.setRole("assistant");
        message.setContent(content);
        CopilotResponse.Choice choice = new CopilotResponse.Choice();
        choice.setIndex(0);
        choice.setMessage(message);
        choice.setFinishReason("stop");
        response.setChoices(List.of(choice));
        return response;
    }
}
