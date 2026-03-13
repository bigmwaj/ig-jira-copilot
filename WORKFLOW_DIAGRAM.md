# AI Orchestration Workflow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         JIRA AI ORCHESTRATION SERVICE                           │
│                   (Spring Boot + Apache Camel + Copilot API)                    │
└─────────────────────────────────────────────────────────────────────────────────┘

╔══════════════════════════════════════════════════════════════════════════════════╗
║  ROUTE 1 ── User Story Refinement                                              ║
╠══════════════════════════════════════════════════════════════════════════════════╣
║                                                                                  ║
║  timer(60s)                                                                      ║
║      │                                                                           ║
║      ▼                                                                           ║
║  JQL: label=AI-Agent AND AI Exchange Tracking ~ "AI01*"                         ║
║      │                                                                           ║
║      ▼                                                                           ║
║  split().parallelProcessing()                                                    ║
║      │                                                                           ║
║      ▼                                                                           ║
║  seda:route1-process (async, concurrentConsumers=5)                              ║
║      │                                                                           ║
║      ├── updateAiExchangeTracking(AI02 – Refinement in progress)                ║
║      │                                                                           ║
║      ├── CopilotClient.sendPrompt(refinementPrompt)  ◄── pollingService         ║
║      │                                                                           ║
║      ├── JiraClient.updateDescription(refinedContent)                           ║
║      │                                                                           ║
║      └── updateAiExchangeTracking(AI03 – Refinement completed)                 ║
║                                                                                  ║
╚══════════════════════════════════════════════════════════════════════════════════╝

╔══════════════════════════════════════════════════════════════════════════════════╗
║  ROUTE 2 ── Development Plan Generation                                        ║
╠══════════════════════════════════════════════════════════════════════════════════╣
║                                                                                  ║
║  timer(60s)                                                                      ║
║      │                                                                           ║
║      ▼                                                                           ║
║  JQL: label=AI-Agent AND AI Exchange Tracking ~ "AI04*"                         ║
║      │                                                                           ║
║      ▼                                                                           ║
║  split().parallelProcessing()                                                    ║
║      │                                                                           ║
║      ▼                                                                           ║
║  seda:route2-process (async, concurrentConsumers=5)                              ║
║      │                                                                           ║
║      ├── updateAiExchangeTracking(AI05 – Dev plan generation in progress)       ║
║      │                                                                           ║
║      ├── CopilotClient.sendPrompt(devPlanPrompt)  ◄── pollingService            ║
║      │                                                                           ║
║      ├── updateAiExchangeTracking(AI06 – Dev plan generated)                   ║
║      │                                                                           ║
║      └── JiraClient.createTask(devPlan) ──► new Jira Task linked to story      ║
║                                                                                  ║
╚══════════════════════════════════════════════════════════════════════════════════╝

╔══════════════════════════════════════════════════════════════════════════════════╗
║  ROUTE 3 ── Development Plan Review                                            ║
╠══════════════════════════════════════════════════════════════════════════════════╣
║                                                                                  ║
║  timer(60s)                                                                      ║
║      │                                                                           ║
║      ▼                                                                           ║
║  JQL: label=AI-Agent AND AI Exchange Tracking ~ "AI07*"                         ║
║      │                                                                           ║
║      ▼                                                                           ║
║  split().parallelProcessing()                                                    ║
║      │                                                                           ║
║      ▼                                                                           ║
║  seda:route3-process (async, concurrentConsumers=5)                              ║
║      │                                                                           ║
║      ├── updateAiExchangeTracking(AI08 – Review in progress)                   ║
║      │                                                                           ║
║      ├── CopilotClient.sendPrompt(reviewPrompt)  ◄── pollingService             ║
║      │                                                                           ║
║      ├── JiraClient.updateDescription(reviewedPlan)                             ║
║      │                                                                           ║
║      └── updateAiExchangeTracking(AI09 – Review completed)                     ║
║                                                                                  ║
╚══════════════════════════════════════════════════════════════════════════════════╝

╔══════════════════════════════════════════════════════════════════════════════════╗
║  ROUTE 4 ── Code Generation                                                    ║
╠══════════════════════════════════════════════════════════════════════════════════╣
║                                                                                  ║
║  timer(60s)                                                                      ║
║      │                                                                           ║
║      ▼                                                                           ║
║  JQL: label=AI-Agent AND AI Exchange Tracking ~ "AI10*"                         ║
║      │                                                                           ║
║      ▼                                                                           ║
║  split().parallelProcessing()                                                    ║
║      │                                                                           ║
║      ▼                                                                           ║
║  seda:route4-process (async, concurrentConsumers=5)                              ║
║      │                                                                           ║
║      ├── updateAiExchangeTracking(AI11 – Code generation in progress)           ║
║      │                                                                           ║
║      ├── CopilotClient.sendPrompt(codeGenPrompt)  ◄── pollingService            ║
║      │                                                                           ║
║      ├── JiraClient.updateDescription(generatedCode)                            ║
║      │                                                                           ║
║      └── updateAiExchangeTracking(AI12 – Code generation completed)            ║
║                                                                                  ║
╚══════════════════════════════════════════════════════════════════════════════════╝


AI Exchange Tracking State Machine
═══════════════════════════════════

User Story lifecycle:
  AI01 ──► AI02 ──► AI03 ──► AI04 ──► AI05 ──► AI06
  Wait     InProg   Done     Wait     InProg   Done
  Refine   Refine   Refine   DevPlan  DevPlan  DevPlan

Task lifecycle:
  AI07 ──► AI08 ──► AI09 ──► AI10 ──► AI11 ──► AI12
  Wait     InProg   Done     Wait     InProg   Done
  Review   Review   Review   Code     Code     Code


Error Handling (all routes)
════════════════════════════
  defaultErrorHandler
    .maximumRedeliveries(3)
    .redeliveryDelay(2000ms)
    .backOffMultiplier(2)      ← exponential back-off


Component Diagram
═════════════════

  ┌──────────────────────────────────────────────────────────┐
  │                    Spring Boot Application                │
  │                                                          │
  │  ┌──────────────────────────────────────────────────┐   │
  │  │                 Apache Camel Context              │   │
  │  │                                                   │   │
  │  │  ┌────────────┐  ┌────────────┐  ┌────────────┐  │   │
  │  │  │  Route 1   │  │  Route 2   │  │  Route 3   │  │   │
  │  │  │ Refinement │  │  DevPlan   │  │   Review   │  │   │
  │  │  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘  │   │
  │  │        │               │               │          │   │
  │  │  ┌─────▼──────────────────────────────▼──────┐   │   │
  │  │  │                Route 4                    │   │   │
  │  │  │             Code Generation               │   │   │
  │  │  └──────────────────┬────────────────────────┘   │   │
  │  └─────────────────────┼────────────────────────────┘   │
  │                        │                                  │
  │  ┌─────────────────────▼────────────────────────────┐   │
  │  │              Service Layer                        │   │
  │  │                                                   │   │
  │  │  ┌─────────────┐  ┌──────────────┐  ┌─────────┐ │   │
  │  │  │ JiraClient  │  │CopilotClient │  │Polling  │ │   │
  │  │  │ REST API v3 │  │ Chat Comp.   │  │Service  │ │   │
  │  │  └──────┬──────┘  └──────┬───────┘  └─────────┘ │   │
  │  └─────────┼───────────────┼──────────────────────────┘  │
  └────────────┼───────────────┼─────────────────────────────┘
               │               │
     ┌─────────▼──┐    ┌───────▼──────┐
     │  Jira REST │    │ Copilot API  │
     │  API (v3)  │    │  (OpenAI-    │
     │  Cloud /   │    │ compatible)  │
     │  Server    │    └──────────────┘
     └────────────┘
```
