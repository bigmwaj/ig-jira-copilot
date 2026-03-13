package ca.espconsulting.igjiracopilot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for a Copilot (OpenAI-compatible) chat completion request.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CopilotRequest {

    private String model;
    private List<Message> messages = new ArrayList<>();
    private double temperature = 0.7;
    private boolean stream = false;

    public CopilotRequest() {}

    public CopilotRequest(String model, String systemPrompt, String userPrompt) {
        this.model = model;
        this.messages.add(new Message("system", systemPrompt));
        this.messages.add(new Message("user", userPrompt));
    }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public List<Message> getMessages() { return messages; }
    public void setMessages(List<Message> messages) { this.messages = messages; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public boolean isStream() { return stream; }
    public void setStream(boolean stream) { this.stream = stream; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private String role;
        private String content;

        public Message() {}

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
