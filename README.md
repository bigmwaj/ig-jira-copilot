# ig-jira-copilot

**Jira AI Copilot Integration Gateway**

A production-ready AI orchestration service that integrates **Jira** and the **GitHub Copilot API** to automate the software development lifecycle using **Spring Boot**, **Apache Camel**, and the **Jira REST API**.

---

## Overview

This application uses Apache Camel routes to orchestrate an end-to-end AI-assisted SDLC workflow:

| Route | Trigger State | Description | Final State |
|-------|--------------|-------------|-------------|
| 1 – User Story Refinement | AI01 | Refines Jira user stories using AI | AI03 |
| 2 – Development Plan Generation | AI04 | Generates a dev plan and creates a linked Jira Task | AI06 |
| 3 – Development Plan Review | AI07 | AI reviews and improves the development plan | AI09 |
| 4 – Code Generation | AI10 | Generates production code from the development plan | AI12 |

All workflow states are tracked in the Jira custom field **"AI Exchange Tracking"**.  
Only issues/tasks labelled `AI-Agent` are processed.

---

## Architecture

```
Spring Boot Application
  └── Apache Camel Context
        ├── Route 1: timer → JQL(AI01) → split/parallel → seda → Copilot → Jira update
        ├── Route 2: timer → JQL(AI04) → split/parallel → seda → Copilot → create Task
        ├── Route 3: timer → JQL(AI07) → split/parallel → seda → Copilot → update Task
        └── Route 4: timer → JQL(AI10) → split/parallel → seda → Copilot → update Task
  └── Service Layer
        ├── JiraClient    – Jira REST API v3 (search, update field, update description, create task)
        ├── CopilotClient – GitHub Copilot / OpenAI-compatible chat completions
        └── PollingService – Configurable polling loop with retry
```

See [WORKFLOW_DIAGRAM.md](WORKFLOW_DIAGRAM.md) for the full ASCII workflow diagram.

---

## Requirements

- Java 17+
- Maven 3.9+
- A Jira Cloud or Server instance with REST API access
- A GitHub Copilot API key (or OpenAI-compatible endpoint)

---

## Configuration

Copy the template and fill in your values, or set environment variables:

```yaml
# src/main/resources/application.yml
app:
  jira:
    base-url: ${JIRA_BASE_URL}
    username: ${JIRA_USERNAME}
    api-token: ${JIRA_API_TOKEN}
    project-key: ${JIRA_PROJECT_KEY}
    ai-exchange-tracking-field: customfield_10100   # your custom field ID
    ai-agent-label: AI-Agent
  copilot:
    api-url: ${COPILOT_API_URL}
    api-key: ${COPILOT_API_KEY}
    model: gpt-4o
    max-poll-attempts: 30
    poll-interval-ms: 5000
  routes:
    scan-interval-ms: 60000
```

### Required environment variables

| Variable | Description |
|----------|-------------|
| `JIRA_BASE_URL` | e.g. `https://mycompany.atlassian.net` |
| `JIRA_USERNAME` | Jira account email |
| `JIRA_API_TOKEN` | API token from Atlassian account settings |
| `JIRA_PROJECT_KEY` | Project key for task creation (e.g. `PROJ`) |
| `COPILOT_API_URL` | Copilot/OpenAI base URL |
| `COPILOT_API_KEY` | Bearer token for Copilot API |

---

## Build & Run

```bash
# Build
mvn clean package -DskipTests

# Run
java -jar target/ig-jira-copilot-1.0.0-SNAPSHOT.jar

# With environment variables
JIRA_BASE_URL=https://mycompany.atlassian.net \
JIRA_USERNAME=me@example.com \
JIRA_API_TOKEN=my-token \
COPILOT_API_KEY=my-copilot-key \
java -jar target/ig-jira-copilot-1.0.0-SNAPSHOT.jar
```

---

## Running Tests

```bash
mvn test
```

---

## Project Structure

```
src/
├── main/
│   ├── java/ca/espconsulting/igjiracopilot/
│   │   ├── IgJiraCopilotApplication.java        # Spring Boot entry point
│   │   ├── config/
│   │   │   ├── AppConfig.java                   # Bean definitions
│   │   │   └── AppProperties.java               # @ConfigurationProperties
│   │   ├── dto/
│   │   │   ├── JiraIssue.java                   # Jira issue DTO
│   │   │   ├── JiraTask.java                    # Jira task creation DTO
│   │   │   ├── CopilotRequest.java              # Copilot API request DTO
│   │   │   └── CopilotResponse.java             # Copilot API response DTO
│   │   ├── service/
│   │   │   ├── JiraClient.java                  # Jira REST API client
│   │   │   ├── CopilotClient.java               # Copilot API client
│   │   │   └── PollingService.java              # AI response polling
│   │   └── route/
│   │       ├── AiState.java                     # AI state constants
│   │       ├── UserStoryRefinementRoute.java    # Route 1
│   │       ├── DevPlanGenerationRoute.java      # Route 2
│   │       ├── DevPlanReviewRoute.java          # Route 3
│   │       └── CodeGenerationRoute.java         # Route 4
│   └── resources/
│       └── application.yml
└── test/
    └── java/ca/espconsulting/igjiracopilot/
        ├── service/
        │   ├── PollingServiceTest.java
        │   ├── CopilotClientTest.java
        │   └── JiraClientTest.java
        └── route/
            └── UserStoryRefinementRouteTest.java
```

---

## Jira Setup

1. Create a custom field called **"AI Exchange Tracking"** (Text Field, single line)
2. Note the field ID (e.g. `customfield_10100`) and set it in `app.jira.ai-exchange-tracking-field`
3. Add the label `AI-Agent` to issues you want processed
4. Set the "AI Exchange Tracking" field to `AI01 - Waiting for refinement` to start the workflow

---

## Error Handling

All routes use `defaultErrorHandler` with:
- **3 redelivery attempts**
- **2-second initial delay** with **2× exponential back-off**
- Warning-level logging on each retry attempt

The `PollingService` retries up to `max-poll-attempts` times with `poll-interval-ms` between attempts.
