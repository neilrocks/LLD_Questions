import java.util.*;

/*
=========================== REQUIREMENTS ===========================

Functional Requirements
1. A user should be able to create an issue against a transaction.
2. Each issue should contain subject, description, transactionId, issueType and status.
3. System should support adding support agents.
4. Each agent can resolve only certain IssueTypes.
5. System should assign issues to agents using a pluggable assignment strategy.
6. If no agent is free, the issue should go to the waitlist of a matching agent.
7. When an issue is resolved, the agent becomes free and the next waitlisted issue should be assigned.
8. System should allow viewing agent work history.

Non Functional Requirements
1. Assignment logic should be extensible → Strategy Pattern.
2. System should have a single central controller → Singleton Pattern.
3. Efficient lookups using HashMaps.
4. Design should be extendable for thread safety.

=========================== CORE ENTITIES ===========================

1. Issue
   Represents a customer complaint raised against a transaction.

2. Agent
   Represents a support agent capable of resolving certain issue types.

3. IssueType
   Enum representing different issue categories.

4. ResolutionType
   Enum representing issue lifecycle states.

5. AgentAssignmentStrategy
   Strategy interface used to plug different assignment algorithms.

6. FirstFreeAgent
   Concrete strategy assigning the first available matching agent.

7. CustomerIssueTrackerApp
   Core service managing issues, agents, assignments, waitlists and history.

8. Main
   Client code demonstrating system usage.

====================================================================
*/

enum IssueType {
    PAYMENT_RELATED,
    MUTUAL_FUND,
    GOLD_RELATED
}

enum ResolutionType {
    OPEN,
    IN_PROGRESS,
    RESOLVED
}

class Issue {

    // Type of issue raised by customer
    private IssueType type;

    // Current status of the issue
    private ResolutionType status;

    // Short summary of the problem
    private String subject;

    // Detailed description of the issue
    private String description;

    // Unique identifier for the issue
    private String id;

    // Transaction against which issue was raised
    private String transactionid;

    // Static counter to generate unique issue IDs
    private static int cnt = 0;

    Issue(String transactionId, IssueType type, String subject, String description) {

        // Initialize issue properties
        this.type = type;
        this.subject = subject;
        this.description = description;

        // Auto generate issue id
        this.id = "I" + (++cnt);

        this.transactionid = transactionId;

        // New issues always start with OPEN status
        this.status = ResolutionType.OPEN;
    }

    // Getter for issue type
    public IssueType getType() { return type; }

    // Getter for issue status
    public ResolutionType getStatus() { return status; }

    // Update the status of the issue
    public void setStatus(ResolutionType status) { this.status = status; }

    // Getter for issue id
    public String getId() { return id; }

    // Getter for transaction id
    public String getTransactionId() { return transactionid; }

    // Used while printing issue details
    public String toString() {
        return "Issue{id=" + id + ", txn=" + transactionid + ", type=" + type + ", status=" + status + "}";
    }
}

class Agent {

    // List of issue types this agent can resolve
    private List<IssueType> type;

    // Agent name
    private String name;

    // Agent email
    private String email;

    // Unique agent identifier
    private String id;

    // Static counter to generate agent IDs
    private static int cnt = 0;

    Agent(String email, String name, List<IssueType> type) {

        // Initialize agent properties
        this.email = email;
        this.name = name;
        this.type = type;

        // Auto generate agent id
        this.id = "A" + (++cnt);
    }

    // Returns supported issue types
    public List<IssueType> getType() { return type; }

    // Returns agent id
    public String getId() { return id; }

    // Used when printing agent info
    public String toString() { return "Agent{id=" + id + ", name=" + name + "}"; }
}

/*
Strategy interface for assigning issues to agents.

Different assignment algorithms can be plugged in without
changing the main system.
*/
interface AgentAssignmentStrategy {

    // Assign an agent for the given issue
    Agent assign(Map<Agent, Boolean> agentAvailMap, Map<String, Issue> issueIdIssueMap, String issueId);
}

/*
Concrete strategy: FirstFreeAgent

This strategy simply finds the first available agent
who supports the issue type and assigns the issue.
*/
class FirstFreeAgent implements AgentAssignmentStrategy {

    @Override
    public Agent assign(Map<Agent, Boolean> agentAvailMap, Map<String, Issue> issueIdIssueMap, String issueId) {

        // Fetch issue details
        Issue issue = issueIdIssueMap.get(issueId);

        // Determine issue type
        IssueType type = issue.getType();

        // Iterate through all agents
        for (Map.Entry<Agent, Boolean> entry : agentAvailMap.entrySet()) {

            Agent agent = entry.getKey();
            boolean isAvailable = entry.getValue();

            // Check if agent is available and supports this issue type
            if (isAvailable && agent.getType().contains(type)) {

                // Mark agent as busy
                agentAvailMap.put(agent, false);

                return agent;
            }
        }

        // No agent available
        return null;
    }
}

/*
Central system managing issues and agents.

Implemented as Singleton so that the entire application
uses a single instance of the issue tracker.
*/
class CustomerIssueTrackerApp {

    // Singleton instance
    private static CustomerIssueTrackerApp instance = null;

    // Map storing issueId → Issue
    private Map<String, Issue> issueIdIssueMap = new HashMap<>();

    // Map storing userEmail → List of issues raised by that user
    private Map<String, List<Issue>> userVsIssueMap = new HashMap<>();

    // Map storing agent → availability status
    private Map<Agent, Boolean> agentAvailMap = new HashMap<>();

    // Map storing agent → waitlisted issues
    private Map<Agent, List<Issue>> agentWaitlistMap = new HashMap<>();

    // Map storing agent → all issues handled historically
    private Map<Agent, List<Issue>> agentHistoryMap = new HashMap<>();

    // Strategy used to assign agents
    private AgentAssignmentStrategy strategy;

    // Private constructor for Singleton
    private CustomerIssueTrackerApp() {}

    // Method to retrieve the single instance
    public static CustomerIssueTrackerApp getInstance() {
        if (instance == null) instance = new CustomerIssueTrackerApp();
        return instance;
    }

    // Allows setting different assignment strategies
    public void setAgentAssignStrategy(AgentAssignmentStrategy str) {
        this.strategy = str;
    }

    /*
    Creates a new issue raised by a user.
    Stores it in both global issue map and user specific map.
    */
    public void createIssue(String transactionId, IssueType type, String subject, String description, String email) {

        Issue issue = new Issue(transactionId, type, subject, description);

        // Store issue globally
        issueIdIssueMap.put(issue.getId(), issue);

        // Store issue under the user
        userVsIssueMap.computeIfAbsent(email, e -> new ArrayList<>()).add(issue);

        System.out.println("Issue " + issue.getId() + " created against transaction " + transactionId);
    }

    /*
    Adds a new support agent to the system.
    Initializes availability, waitlist and history tracking.
    */
    public void addAgent(String email, String name, List<IssueType> types) {

        Agent agent = new Agent(email, name, types);

        // Agent starts as available
        agentAvailMap.put(agent, true);

        // Initialize empty waitlist and history
        agentWaitlistMap.put(agent, new ArrayList<>());
        agentHistoryMap.put(agent, new ArrayList<>());

        System.out.println("Agent " + agent.getId() + " created");
    }

    /*
    Assigns an issue to an agent using the selected strategy.
    */
    public void assignIssue(String issueId) {

        Issue issue = issueIdIssueMap.get(issueId);

        // Use strategy to find suitable agent
        Agent agent = strategy.assign(agentAvailMap, issueIdIssueMap, issueId);

        if (agent != null) {

            // Update issue status
            updateIssue(issueId, ResolutionType.IN_PROGRESS, "Assigned to agent");

            // Add issue to agent work history
            agentHistoryMap.get(agent).add(issue);

            System.out.println("Issue " + issueId + " assigned to agent " + agent.getId());

        } else {

            // If no agent free, add issue to waitlist
            for (Agent ag : agentAvailMap.keySet()) {

                if (ag.getType().contains(issue.getType())) {

                    agentWaitlistMap.get(ag).add(issue);

                    System.out.println("Issue " + issueId + " added to waitlist of Agent " + ag.getId());

                    return;
                }
            }

            System.out.println("No suitable agent found for issue " + issueId);
        }
    }

    /*
    Updates the status of an issue.
    */
    public void updateIssue(String issueId, ResolutionType type, String comment) {

        Issue issue = issueIdIssueMap.get(issueId);

        issue.setStatus(type);

        System.out.println(issueId + " status updated to " + issue.getStatus());
    }

    /*
    Marks issue as resolved.

    After resolving:
    1. Agent becomes free
    2. Next issue from agent waitlist gets assigned automatically
    */
    public void resolveIssue(String issueId, String comment) {

        Issue issue = issueIdIssueMap.get(issueId);

        issue.setStatus(ResolutionType.RESOLVED);

        // Free agent and check waitlist
        for (Map.Entry<Agent, Boolean> entry : agentAvailMap.entrySet()) {

            Agent agent = entry.getKey();

            if (!entry.getValue() && agent.getType().contains(issue.getType())) {

                // Mark agent free
                agentAvailMap.put(agent, true);

                // Assign next issue from waitlist
                List<Issue> waitlist = agentWaitlistMap.get(agent);

                if (!waitlist.isEmpty()) {

                    Issue nextIssue = waitlist.remove(0);

                    assignIssue(nextIssue.getId());
                }
            }
        }

        System.out.println(issueId + " issue marked as resolved");
    }

    /*
    Prints the entire work history of each agent.
    */
    public void viewAgentsWorkHistory() {

        for (Map.Entry<Agent, List<Issue>> entry : agentHistoryMap.entrySet()) {

            Agent agent = entry.getKey();
            List<Issue> history = entry.getValue();

            System.out.println("Work history of " + agent.getId() + ": " + history);
        }
    }
}

/*
Client code demonstrating the usage of the system.
*/
class Main {

    public static void main(String[] args) {

        // Get singleton instance
        CustomerIssueTrackerApp app = CustomerIssueTrackerApp.getInstance();

        // Create issues
        app.createIssue("T1", IssueType.PAYMENT_RELATED, "Payment Failed", "Debited but not credited", "u1@test.com");
        app.createIssue("T2", IssueType.MUTUAL_FUND, "Purchase Failed", "Unable to buy MF", "u2@test.com");
        app.createIssue("T3", IssueType.PAYMENT_RELATED, "Payment Failed", "Debited but not credited", "u2@test.com");

        // Add agents
        app.addAgent("a1@test.com", "Agent 1", Arrays.asList(IssueType.PAYMENT_RELATED, IssueType.GOLD_RELATED));
        app.addAgent("a2@test.com", "Agent 2", Arrays.asList(IssueType.PAYMENT_RELATED));

        // Set assignment strategy
        app.setAgentAssignStrategy(new FirstFreeAgent());

        // Assign issues
        app.assignIssue("I1");
        app.assignIssue("I2");
        app.assignIssue("I3");

        // Resolve issue
        app.resolveIssue("I1", "Resolved payment");

        // View work history
        app.viewAgentsWorkHistory();
    }
}

/*
Thread Safety Improvement (if required)

To make this system thread-safe:
1. Replace HashMap with ConcurrentHashMap.
2. Use synchronized methods OR
3. Use per-agent ReentrantLocks so that
   different agents can process issues concurrently
   without blocking the entire system.
*/