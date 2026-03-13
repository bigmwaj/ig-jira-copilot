package ca.espconsulting.igjiracopilot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO used when creating a new Jira Task via the REST API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraTask {

    private String projectKey;
    private String summary;
    private String description;
    private String linkedIssueKey;
    private String linkType;

    public JiraTask() {}

    public JiraTask(String projectKey, String summary, String description, String linkedIssueKey) {
        this.projectKey = projectKey;
        this.summary = summary;
        this.description = description;
        this.linkedIssueKey = linkedIssueKey;
        this.linkType = "is subtask of";
    }

    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLinkedIssueKey() { return linkedIssueKey; }
    public void setLinkedIssueKey(String linkedIssueKey) { this.linkedIssueKey = linkedIssueKey; }

    public String getLinkType() { return linkType; }
    public void setLinkType(String linkType) { this.linkType = linkType; }

    @Override
    public String toString() {
        return "JiraTask{projectKey='" + projectKey + "', summary='" + summary
                + "', linkedIssueKey='" + linkedIssueKey + "'}";
    }
}
