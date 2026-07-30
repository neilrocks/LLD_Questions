import java.util.*;

/*
Rollout Strategy
----------------
Goal:
Safely update replicas while ensuring:
1. Replicas are updated evenly across regions.
2. At least minHealthy replicas remain healthy during every batch.

Algorithm:
1. Group all replicas by their region.
2. Repeat until every replica has been updated:
   a. Create an empty batch.
   b. Visit each region (round-robin).
   c. Pick at most one replica from that region.
   d. Before adding it, check whether taking it down would violate
      the minimum healthy replica constraint.
   e. If safe, add it to the batch.
3. Update all replicas in the batch in parallel (simulated here sequentially).
4. After the batch completes, replicas become healthy again.
5. Continue until all region lists become empty.

Example:
US   : A1 A2
EU   : B1 B2
APAC : C1 C2

Batch 1 -> A1 B1
Batch 2 -> A2 B2
Batch 3 -> C1
Batch 4 -> C2
*/

class Replica {
    private String id;
    private String region;
    private boolean healthy = true;

    public Replica(String id, String region) {
        this.id = id;
        this.region = region;
    }

    public String getRegion() {
        return region;
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void update() {
        healthy = false;
        System.out.println("Updating " + id + " (" + region + ")");
        // Simulate update
        healthy = true;
        System.out.println(id + " updated successfully.");
    }

    @Override
    public String toString() {
        return id;
    }
}

class RolloutManager {

    private int minHealthy;

    public RolloutManager(int minHealthy) {
        this.minHealthy = minHealthy;
    }

    public void rollout(List<Replica> replicas) {

        // Group replicas by region
        Map<String, List<Replica>> regionMap = new LinkedHashMap<>();

        for (Replica r : replicas) {
            regionMap.computeIfAbsent(r.getRegion(), k -> new ArrayList<>()).add(r);
        }

        boolean replicasRemaining = true;

        while (replicasRemaining) {

            replicasRemaining = false;
            List<Replica> batch = new ArrayList<>();

            // Pick one replica from every region
            for (List<Replica> regionReplicas : regionMap.values()) {

                if (regionReplicas.isEmpty())
                    continue;

                replicasRemaining = true;

                // Check if one more replica can be taken down
                if (healthyReplicas(replicas) - batch.size() > minHealthy) {
                    batch.add(regionReplicas.remove(0));
                }
            }

            if (batch.isEmpty()) {
                System.out.println("Waiting for healthy replicas...");
                break;
            }

            System.out.println("\nProcessing Batch : " + batch);

            // Parallel update can be done here.
            // For simplicity updating sequentially.
            for (Replica r : batch) {
                r.update();
            }
        }

        System.out.println("\nRollout Completed Successfully.");
    }

    private int healthyReplicas(List<Replica> replicas) {

        int count = 0;

        for (Replica r : replicas) {
            if (r.isHealthy())
                count++;
        }

        return count;
    }
}

public class Main {

    public static void main(String[] args) {

        List<Replica> replicas = Arrays.asList(
                new Replica("A1", "US"),
                new Replica("A2", "US"),
                new Replica("B1", "EU"),
                new Replica("B2", "EU"),
                new Replica("C1", "APAC"),
                new Replica("C2", "APAC")
        );

        RolloutManager manager = new RolloutManager(4);

        manager.rollout(replicas);
    }
}