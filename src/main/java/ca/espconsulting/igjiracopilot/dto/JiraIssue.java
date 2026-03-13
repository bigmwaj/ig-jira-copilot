package ca.espconsulting.igjiracopilot.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DTO representing a Jira issue returned by the REST API.
 *
 * <p>Custom field values (e.g. "AI Exchange Tracking") are captured in a
 * {@code customFields} map and resolved at runtime via
 * {@link Fields#getAiExchangeTracking(String)} to avoid coupling the DTO to a
 * specific Jira instance's custom field ID.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraIssue {

    private String id;
    private String key;
    private Fields fields;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public Fields getFields() { return fields; }
    public void setFields(Fields fields) { this.fields = fields; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fields {
        private String summary;
        private Description description;
        private List<String> labels;
        private IssueType issuetype;

        /** Captures all Jira custom fields (e.g. customfield_10100) dynamically. */
        private final Map<String, Object> customFields = new HashMap<>();

        @JsonAnySetter
        public void setCustomField(String name, Object value) {
            customFields.put(name, value);
        }

        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }

        public Description getDescription() { return description; }
        public void setDescription(Description description) { this.description = description; }

        public List<String> getLabels() { return labels; }
        public void setLabels(List<String> labels) { this.labels = labels; }

        public IssueType getIssuetype() { return issuetype; }
        public void setIssuetype(IssueType issuetype) { this.issuetype = issuetype; }

        /**
         * Returns the value of the "AI Exchange Tracking" custom field using the
         * configured field ID (e.g. {@code customfield_10100}).
         *
         * @param fieldId the Jira custom field ID from {@code AppProperties}
         * @return the tracking state string, or {@code null} if not set
         */
        public String getAiExchangeTracking(String fieldId) {
            Object value = customFields.get(fieldId);
            return value != null ? value.toString() : null;
        }

        /**
         * Directly set the AI Exchange Tracking value (used in tests and Jira update
         * flows where the field ID is not needed for deserialization).
         */
        public void setAiExchangeTracking(String value) {
            customFields.put("aiExchangeTracking", value);
        }

        /**
         * Returns the raw custom fields map (useful for testing/debugging).
         */
        public Map<String, Object> getCustomFields() { return customFields; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Description {
        private String type;
        private List<Map<String, Object>> content;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public List<Map<String, Object>> getContent() { return content; }
        public void setContent(List<Map<String, Object>> content) { this.content = content; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IssueType {
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    /**
     * Extracts a plain-text representation of the description content.
     */
    public String getDescriptionText() {
        if (fields == null || fields.getDescription() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        List<Map<String, Object>> content = fields.getDescription().getContent();
        if (content != null) {
            extractText(content, sb);
        }
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private void extractText(List<Map<String, Object>> nodes, StringBuilder sb) {
        for (Map<String, Object> node : nodes) {
            if ("text".equals(node.get("type"))) {
                sb.append(node.get("text")).append(" ");
            }
            Object inner = node.get("content");
            if (inner instanceof List) {
                extractText((List<Map<String, Object>>) inner, sb);
            }
        }
    }

    @Override
    public String toString() {
        return "JiraIssue{id='" + id + "', key='" + key + "', summary='"
                + (fields != null ? fields.getSummary() : "") + "'}";
    }
}
