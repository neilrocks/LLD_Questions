/**
 * FUNCTION REQUIREMENTS
 * ---------------------
 * 1. Carrier deposits a package by specifying size (SMALL, MEDIUM, LARGE). [page:1]
 *    - System assigns an available compartment of matching size. [page:1]
 *    - System opens that compartment and returns an access token code, or error if no space. [page:1]
 *
 * 2. Upon successful deposit, an access token is generated and returned. [page:1]
 *    - One access token per package. [page:1]
 *
 * 3. User retrieves package by entering access token code. [page:1]
 *    - System validates code and opens corresponding compartment. [page:1]
 *    - Throws specific error if code is invalid or expired. [page:1]
 *
 * 4. Access tokens expire after 7 days. [page:1]
 *    - Expired codes are rejected if used for pickup. [page:1]
 *    - Package remains in compartment until staff removes it. [page:1]
 *
 * 5. Staff can open all expired compartments to manually handle packages. [page:1]
 *    - System opens all compartments whose tokens have expired. [page:1]
 *    - Staff physically removes packages and returns them to sender (outside scope). [page:1]
 *
 * 6. Invalid access tokens are rejected with clear error messages. [page:1]
 *    - Wrong code, already used, or expired -> user gets specific feedback. [page:1]
 *
 * OUT OF SCOPE
 * ------------
 * - How the package gets to the locker (delivery logistics). [page:1]
 * - How the access token reaches the customer (SMS/email notification). [page:1]
 * - Lockout after failed access token attempts. [page:1]
 * - UI / rendering layer. [page:1]
 * - Multiple locker stations. [page:1]
 * - Payment or pricing. [page:1]
 *
 * CORE ENTITIES
 * -------------
 * 1. Locker [page:1]
 *    - Orchestrator and public API of the system. [page:1]
 *    - Owns all compartments and a mapping from access token code to AccessToken. [page:1]
 *    - Responsible for:
 *        * depositPackage(size) -> String | error. [page:1]
 *        * pickup(tokenCode) -> void | error. [page:1]
 *        * openExpiredCompartments() -> void. [page:1]
 *
 * 2. AccessToken [page:1]
 *    - Represents bearer token for compartment access. [page:1]
 *    - Holds:
 *        * code (String). [page:1]
 *        * expiration timestamp. [page:1]
 *        * reference to Compartment it unlocks. [page:1]
 *    - Responsibilities:
 *        * isExpired() -> boolean. [page:1]
 *        * getCompartment(), getCode(). [page:1]
 *
 * 3. Compartment [page:1]
 *    - Physical locker slot. [page:1]
 *    - Holds:
 *        * size (Size). [page:1]
 *        * occupied flag (boolean) representing physical package presence. [page:1]
 *    - Responsibilities:
 *        * getSize(), isOccupied(). [page:1]
 *        * markOccupied(), markFree(). [page:1]
 *        * open() -> triggers door unlock (here, just simulated). [page:1]
 *
 * 4. Size enum [page:1]
 *    - SMALL, MEDIUM, LARGE. [page:1]
 *
 * DESIGN NOTES
 * ------------
 * - Locker tracks relational state (mapping tokenCode -> AccessToken). [page:1]
 * - Compartment tracks physical state (occupied vs free). [page:1]
 * - AccessToken encapsulates expiration logic. [page:1]
 * - depositPackage returns only the token code (driver sees which door opened). [page:1]
 * - pickup returns void; success feedback is the door opening. [page:1]
 */

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Entry point with a small demo.
 */
public class AmazonLockerSystem {

    public static void main(String[] args) {
        // Create some compartments for the demo
        List<Compartment> compartments = new ArrayList<>();
        compartments.add(new Compartment(Size.SMALL));
        compartments.add(new Compartment(Size.MEDIUM));
        compartments.add(new Compartment(Size.MEDIUM));
        compartments.add(new Compartment(Size.LARGE));

        Locker locker = new Locker(compartments);

        try {
            // Deposit a MEDIUM package
            String token = locker.depositPackage(Size.MEDIUM);
            System.out.println("Generated access token: " + token);

            // Pick up with the same token
            locker.pickup(token);
            System.out.println("Pickup successful for token: " + token);

        } catch (LockerException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}

/**
 * Locker orchestrates deposit, pickup, and expired-compartment operations. [page:1]
 */
class Locker {

    private final List<Compartment> compartments;
    private final Map<String, AccessToken> accessTokenMapping;

    public Locker(List<Compartment> compartments) {
        this.compartments = Objects.requireNonNull(compartments);
        this.accessTokenMapping = new HashMap<>();
    }

    /**
     * Carrier deposits a package by specifying size. [page:1]
     * - Finds an available compartment of that size. [page:1]
     * - Opens compartment, marks it occupied, generates and stores access token. [page:1]
     * - Returns access token code. [page:1]
     *
     * @throws LockerException if no compartment is available. [page:1]
     */
    public String depositPackage(Size size) {
        Compartment compartment = getAvailableCompartment(size);
        if (compartment == null) {
            throw new LockerException("No available compartment of size " + size);
        }

        // Open door so driver can deposit the package. [page:1]
        compartment.open();

        // Mark the compartment as occupied (expects driver to actually place package). [page:1]
        compartment.markOccupied();

        // Generate token with 7-day expiration. [page:1]
        AccessToken accessToken = generateAccessToken(compartment);

        // Store mapping for fast lookup by token code. [page:1]
        accessTokenMapping.put(accessToken.getCode(), accessToken);

        // Return token code (driver doesn't need compartment ID). [page:1]
        return accessToken.getCode();
    }

    /**
     * User retrieves package by entering access token. [page:1]
     * - Validates token: checks non-empty, existence, and expiry. [page:1]
     * - If valid, opens compartment and clears deposit state. [page:1]
     *
     * @throws LockerException with clear message on invalid or expired codes. [page:1]
     */
    public void pickup(String tokenCode) {
        if (tokenCode == null || tokenCode.isEmpty()) {
            throw new LockerException("Invalid access token code");
        }

        AccessToken accessToken = accessTokenMapping.get(tokenCode);
        if (accessToken == null) {
            // Covers both never-existing and already-used tokens. [page:1]
            throw new LockerException("Invalid access token code");
        }

        if (accessToken.isExpired()) {
            // Token remains in mapping; package still inside compartment. [page:1]
            throw new LockerException("Access token has expired");
        }

        // Valid pickup: open door and clean up deposit state. [page:1]
        Compartment compartment = accessToken.getCompartment();
        compartment.open();
        clearDeposit(accessToken);
    }

    /**
     * Staff operation: open all compartments whose tokens have expired. [page:1]
     * Note: does not clear state; staff would later mark packages removed. [page:1]
     */
    public void openExpiredCompartments() {
        for (AccessToken accessToken : accessTokenMapping.values()) {
            if (accessToken.isExpired()) {
                Compartment compartment = accessToken.getCompartment();
                compartment.open();
            }
        }
    }

    /**
     * Finds first free compartment whose size matches the requested size. [page:1]
     * Time complexity: O(n) over number of compartments. [page:1]
     */
    private Compartment getAvailableCompartment(Size size) {
        for (Compartment c : compartments) {
            if (c.getSize() == size && !c.isOccupied()) {
                return c;
            }
        }
        return null;
    }

    /**
     * Generates an access token with random 6-digit code and 7-day expiration. [page:1]
     */
    private AccessToken generateAccessToken(Compartment compartment) {
        String code = String.format("%06d", new Random().nextInt(1_000_000));
        Instant expiration = Instant.now().plus(Duration.ofDays(7));
        return new AccessToken(code, expiration, compartment);
    }

    /**
     * Clears deposit state after successful pickup: [page:1]
     * - marks compartment free. [page:1]
     * - removes token from mapping. [page:1]
     */
    private void clearDeposit(AccessToken accessToken) {
        Compartment compartment = accessToken.getCompartment();
        compartment.markFree();
        accessTokenMapping.remove(accessToken.getCode());
    }
}

/**
 * AccessToken encapsulates code, expiration, and associated compartment. [page:1]
 * It owns the expiry logic. [page:1]
 */
class AccessToken {

    private final String code;
    private final Instant expiration;
    private final Compartment compartment;

    public AccessToken(String code, Instant expiration, Compartment compartment) {
        this.code = Objects.requireNonNull(code);
        this.expiration = Objects.requireNonNull(expiration);
        this.compartment = Objects.requireNonNull(compartment);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiration) || Instant.now().equals(expiration);
    }

    public Compartment getCompartment() {
        return compartment;
    }

    public String getCode() {
        return code;
    }
}

/**
 * Compartment represents a physical locker slot with size and occupancy state. [page:1]
 */
class Compartment {

    private final Size size;
    private boolean occupied;

    public Compartment(Size size) {
        this.size = Objects.requireNonNull(size);
        this.occupied = false;
    }

    public Size getSize() {
        return size;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public void markOccupied() {
        this.occupied = true;
    }

    public void markFree() {
        this.occupied = false;
    }

    /**
     * Simulates opening the physical door. [page:1]
     * In a real system, this would invoke hardware APIs. [page:1]
     */
    public void open() {
        System.out.println("Opening compartment (" + size + ")");
    }
}

/**
 * Size categories supported by the locker system. [page:1]
 */
enum Size {
    SMALL,
    MEDIUM,
    LARGE
}

/**
 * Simple runtime exception for locker-related errors with clear messages. [page:1]
 */
class LockerException extends RuntimeException {
    public LockerException(String message) {
        super(message);
    }
}