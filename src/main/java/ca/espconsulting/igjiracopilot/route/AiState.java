package ca.espconsulting.igjiracopilot.route;

/**
 * Constants for the "AI Exchange Tracking" Jira custom field states.
 */
public final class AiState {

    private AiState() {}

    // Route 1: User Story Refinement
    public static final String AI01_WAITING_REFINEMENT      = "AI01 - Waiting for refinement";
    public static final String AI02_REFINEMENT_IN_PROGRESS  = "AI02 - Refinement in progress";
    public static final String AI03_REFINEMENT_COMPLETED    = "AI03 - Refinement completed";

    // Route 2: Development Plan Generation
    public static final String AI04_WAITING_DEV_PLAN        = "AI04 - Waiting for development plan";
    public static final String AI05_DEV_PLAN_IN_PROGRESS    = "AI05 - Dev plan generation in progress";
    public static final String AI06_DEV_PLAN_GENERATED      = "AI06 - Dev plan generated";

    // Route 3: Development Plan Review
    public static final String AI07_WAITING_REVIEW          = "AI07 - Waiting for dev plan review";
    public static final String AI08_REVIEW_IN_PROGRESS      = "AI08 - Review in progress";
    public static final String AI09_REVIEW_COMPLETED        = "AI09 - Review completed";

    // Route 4: Code Generation
    public static final String AI10_WAITING_CODE_GEN        = "AI10 - Waiting for code generation";
    public static final String AI11_CODE_GEN_IN_PROGRESS    = "AI11 - Code generation in progress";
    public static final String AI12_CODE_GEN_COMPLETED      = "AI12 - Code generation completed";

    /** JQL prefix for matching AI Exchange Tracking values. */
    public static final String PREFIX_AI01 = "AI01";
    public static final String PREFIX_AI04 = "AI04";
    public static final String PREFIX_AI07 = "AI07";
    public static final String PREFIX_AI10 = "AI10";
}
