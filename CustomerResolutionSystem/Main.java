import java.util.*;

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
    private IssueType type;
    private ResolutionType status;
    private String subject;
    private String description;
    private String id;
    private String transactionid;
    private static int cnt = 0;

    Issue(String transactionId, IssueType type, String subject, String description) {
        this.type = type;
        this.subject = subject;
        this.description = description;
        this.id = "I" + (++cnt);
        this.transactionid = transactionId;
        this.status = ResolutionType.OPEN;
    }

    public IssueType getType() { return type; }
    public ResolutionType getStatus() { return status; }
    public void setStatus(ResolutionType status) { this.status = status; }
    public String getId() { return id; }
    public String getTransactionId() { return transactionid; }

    public String toString() {
        return "Issue{id=" + id + ", txn=" + transactionid + ", type=" + type + ", status=" + status + "}";
    }
}

class Agent {
    private List<IssueType> type;
    private String name;
    private String email;
    private String id;
    private static int cnt = 0;

    Agent(String email, String name, List<IssueType> type) {
        this.email = email;
        this.name = name;
        this.type = type;
        this.id = "A" + (++cnt);
    }

    public List<IssueType> getType() { return type; }
    public String getId() { return id; }
    public String toString() { return "Agent{id=" + id + ", name=" + name + "}"; }
}

interface AgentAssignmentStrategy {
    Agent assign(Map<Agent, Boolean> agentAvailMap, Map<String, Issue> issueIdIssueMap, String issueId);
}

class FirstFreeAgent implements AgentAssignmentStrategy {
    @Override
    public Agent assign(Map<Agent, Boolean> agentAvailMap, Map<String, Issue> issueIdIssueMap, String issueId) {
        Issue issue = issueIdIssueMap.get(issueId);
        IssueType type = issue.getType();

        for (Map.Entry<Agent, Boolean> entry : agentAvailMap.entrySet()) {
            Agent agent = entry.getKey();
            boolean isAvailable = entry.getValue();
            if (isAvailable && agent.getType().contains(type)) {
                agentAvailMap.put(agent, false);
                return agent;
            }
        }
        return null;
    }
}

class CustomerIssueTrackerApp {
    private static CustomerIssueTrackerApp instance = null;
    private Map<String, Issue> issueIdIssueMap = new HashMap<>();
    private Map<String, List<Issue>> userVsIssueMap = new HashMap<>();
    private Map<Agent, Boolean> agentAvailMap = new HashMap<>();
    private Map<Agent, List<Issue>> agentWaitlistMap = new HashMap<>();
    private Map<Agent, List<Issue>> agentHistoryMap = new HashMap<>();
    private AgentAssignmentStrategy strategy;

    private CustomerIssueTrackerApp() {}
    public static CustomerIssueTrackerApp getInstance() {
        if (instance == null) instance = new CustomerIssueTrackerApp();
        return instance;
    }

    public void setAgentAssignStrategy(AgentAssignmentStrategy str) { this.strategy = str; }

    public void createIssue(String transactionId, IssueType type, String subject, String description, String email) {
        Issue issue = new Issue(transactionId, type, subject, description);
        issueIdIssueMap.put(issue.getId(), issue);
        userVsIssueMap.computeIfAbsent(email, e -> new ArrayList<>()).add(issue);
        System.out.println("Issue " + issue.getId() + " created against transaction " + transactionId);
    }

    public void addAgent(String email, String name, List<IssueType> types) {
        Agent agent = new Agent(email, name, types);
        agentAvailMap.put(agent, true);
        agentWaitlistMap.put(agent, new ArrayList<>());
        agentHistoryMap.put(agent, new ArrayList<>());
        System.out.println("Agent " + agent.getId() + " created");
    }

    public void assignIssue(String issueId) {
        Issue issue = issueIdIssueMap.get(issueId);
        Agent agent = strategy.assign(agentAvailMap, issueIdIssueMap, issueId);

        if (agent != null) {
            updateIssue(issueId, ResolutionType.IN_PROGRESS, "Assigned to agent");
            agentHistoryMap.get(agent).add(issue);
            System.out.println("Issue " + issueId + " assigned to agent " + agent.getId());
        } else {
            // put into waitlist of first matching agent
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

    public void updateIssue(String issueId, ResolutionType type, String comment) {
        Issue issue = issueIdIssueMap.get(issueId);
        issue.setStatus(type);
        System.out.println(issueId + " status updated to " + issue.getStatus());
    }

    public void resolveIssue(String issueId, String comment) {
        Issue issue = issueIdIssueMap.get(issueId);
        issue.setStatus(ResolutionType.RESOLVED);

        // free up agent and assign next waitlisted issue
        for (Map.Entry<Agent, Boolean> entry : agentAvailMap.entrySet()) {
            Agent agent = entry.getKey();
            if (!entry.getValue() && agent.getType().contains(issue.getType())) {
                agentAvailMap.put(agent, true);

                List<Issue> waitlist = agentWaitlistMap.get(agent);
                if (!waitlist.isEmpty()) {
                    Issue nextIssue = waitlist.remove(0);
                    assignIssue(nextIssue.getId());
                }
            }
        }
        System.out.println(issueId + " issue marked as resolved");
    }

    public void viewAgentsWorkHistory() {
        for (Map.Entry<Agent, List<Issue>> entry : agentHistoryMap.entrySet()) {
            Agent agent = entry.getKey();
            List<Issue> history = entry.getValue();
            System.out.println("Work history of " + agent.getId() + ": " + history);
        }
    }
}

class Main {
    public static void main(String[] args) {
        CustomerIssueTrackerApp app = CustomerIssueTrackerApp.getInstance();

        app.createIssue("T1", IssueType.PAYMENT_RELATED, "Payment Failed", "Debited but not credited", "u1@test.com");
        app.createIssue("T2", IssueType.MUTUAL_FUND, "Purchase Failed", "Unable to buy MF", "u2@test.com");
        app.createIssue("T3", IssueType.PAYMENT_RELATED, "Payment Failed", "Debited but not credited", "u2@test.com");

        app.addAgent("a1@test.com", "Agent 1", Arrays.asList(IssueType.PAYMENT_RELATED, IssueType.GOLD_RELATED));
        app.addAgent("a2@test.com", "Agent 2", Arrays.asList(IssueType.PAYMENT_RELATED));

        app.setAgentAssignStrategy(new FirstFreeAgent());

        app.assignIssue("I1");
        app.assignIssue("I2");
        app.assignIssue("I3"); // will go to waitlist

        app.resolveIssue("I1", "Resolved payment");
        app.viewAgentsWorkHistory();
    }
}
/*To make this thread-safe, I would either synchronize the core methods for simplicity,
or for scalability use ConcurrentHashMap 
plus per-agent ReentrantLocks, so that different agents’ 
workloads can be modified in parallel without blocking the whole system */