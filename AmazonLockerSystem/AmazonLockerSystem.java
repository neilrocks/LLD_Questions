package AmazonLockerSystem;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

// ============================ Domain Entities ============================
class Customer {
    String name;
    String pincode;

    public Customer(String name, String pincode) {
        this.name = name;
        this.pincode = pincode;
    }
}

class Parcel {
    String id;
    String size;

    public Parcel(String id, String size) {
        this.id = id;
        this.size = size;
    }
}

class Slot {
    String slotId;
    String size;
    AtomicBoolean isAvailable = new AtomicBoolean(true);

    public Slot(String slotId, String size) {
        this.slotId = slotId;
        this.size = size;
    }

    public boolean tryAssign() {
        return isAvailable.compareAndSet(true, false);
    }

    public void release() {
        isAvailable.compareAndSet(false, true);
    }

    public boolean isAvailable() {
        return isAvailable.get();
    }
}

class LockerMachine {
    String id;
    String pincode;
    List<Slot> slots = new ArrayList<>();
    LockerState currentState;

    public LockerMachine(String id, String pincode) {
        this.id = id;
        this.pincode = pincode;
        this.currentState = new IdleState(this);
    }

    public void touchScreen() {
        currentState.touchScreen();
    }

    public void selectMode(String mode) {
        currentState.selectMode(mode);
    }

    public void scanPackage(String packageId) {
        currentState.scanPackage(packageId);
    }

    public void enterOTP(String otp) {
        currentState.enterOTP(otp);
    }

    public void setState(LockerState newState) {
        this.currentState = newState;
    }
}

// ============================ State Design Pattern ============================
interface LockerState {
    void touchScreen();
    void selectMode(String mode);
    void scanPackage(String packageId);
    void enterOTP(String otp);
}

class IdleState implements LockerState {
    LockerMachine locker;

    public IdleState(LockerMachine locker) {
        this.locker = locker;
    }

    public void touchScreen() {
        System.out.println("Locker screen touched. Moving to SelectModeState.");
        locker.setState(new SelectModeState(locker));
    }

    public void selectMode(String mode) { System.out.println("Invalid operation. Please touch screen first."); }
    public void scanPackage(String packageId) { System.out.println("Invalid operation in Idle State."); }
    public void enterOTP(String otp) { System.out.println("Invalid operation in Idle State."); }
}

class SelectModeState implements LockerState {
    LockerMachine locker;

    public SelectModeState(LockerMachine locker) {
        this.locker = locker;
    }

    public void touchScreen() { System.out.println("Already in Select Mode."); }

    public void selectMode(String mode) {
        if (mode.equalsIgnoreCase("Delivery")) {
            System.out.println("Agent selected Delivery mode.");
            locker.setState(new AgentDeliveryState(locker));
        } else if (mode.equalsIgnoreCase("Pickup")) {
            System.out.println("Customer selected Pickup mode.");
            locker.setState(new CustomerPickupState(locker));
        } else {
            System.out.println("Invalid mode selected.");
        }
    }

    public void scanPackage(String packageId) { System.out.println("Please select mode first."); }
    public void enterOTP(String otp) { System.out.println("Please select mode first."); }
}

class AgentDeliveryState implements LockerState {
    LockerMachine locker;

    public AgentDeliveryState(LockerMachine locker) {
        this.locker = locker;
    }

    public void touchScreen() { System.out.println("Already in Delivery mode."); }
    public void selectMode(String mode) { System.out.println("Already in Delivery mode."); }

    public void scanPackage(String packageId) {
        System.out.println("Scanning package: " + packageId);
        String assignedSlotId = LockerService.getAssignedSlot(packageId);
        
        if (assignedSlotId != null) {
            System.out.println("Package " + packageId + " assigned to pre-reserved slot " + assignedSlotId);
            String otp = OTPValidationService.generateOTP(packageId);
            NotificationService.sendNotification("OTP for pickup: " + otp);
            System.out.println("Door opened for delivery. After placing package, door closed.");
        } else {
            System.out.println("No slot reservation found for package: " + packageId);
        }

        locker.setState(new IdleState(locker));
    }

    public void enterOTP(String otp) { System.out.println("Invalid in Delivery State."); }
}

class CustomerPickupState implements LockerState {
    LockerMachine locker;

    public CustomerPickupState(LockerMachine locker) {
        this.locker = locker;
    }

    public void touchScreen() { System.out.println("Already in Pickup mode."); }
    public void selectMode(String mode) { System.out.println("Already in Pickup mode."); }

    public void scanPackage(String packageId) { System.out.println("Not allowed in Pickup mode."); }

    public void enterOTP(String otp) {
        System.out.println("Customer entered OTP: " + otp);
        if (OTPValidationService.validateOTP(otp)) {
            System.out.println("OTP validated successfully. Door opened for pickup.");
            String packageId = OTPValidationService.getPackageIdFromOTP(otp);
            if (packageId != null) {
                String slotId = LockerService.getAssignedSlot(packageId);
                if (slotId != null) {
                    System.out.println("Package picked up from slot: " + slotId + ". Door closed. Slot released.");
                    // Clean up mappings
                    LockerService.removePackageMapping(packageId);
                    OTPValidationService.removeOTP(otp);
                    
                    // Release the slot
                    for (Slot slot : locker.slots) {
                        if (slot.slotId.equals(slotId)) {
                            slot.release();
                            break;
                        }
                    }
                }
            }
        } else {
            System.out.println("Invalid or expired OTP.");
        }
        locker.setState(new IdleState(locker));
    }
}

// ============================ Services ============================
class LockerService {
    List<LockerMachine> lockers = new ArrayList<>();
    private static Map<String, String> packageToSlotMapping = new HashMap<>(); // packageId -> slotId

    public void addLocker(LockerMachine locker) {
        lockers.add(locker);
    }

    public List<LockerMachine> findEligibleLockers(String pincode, String packageSize) {
        System.out.println("Finding eligible lockers for pincode: " + pincode + " and package size: " + packageSize);
        List<LockerMachine> eligible = new ArrayList<>();
        for (LockerMachine locker : lockers) {
            if (locker.pincode.equals(pincode)) {
                for (Slot slot : locker.slots) {
                    if (slot.isAvailable() && slot.size.equals(packageSize)) {
                        eligible.add(locker);
                        break;
                    }
                }
            }
        }
        return eligible;
    }

    public void placeOrder(Customer customer, Parcel parcel, LockerMachine selectedLocker) {
        System.out.println("Order placed by customer: " + customer.name + " for parcel: " + parcel.id);
        Slot assignedSlot = null;
        for (Slot slot : selectedLocker.slots) {
            if (slot.isAvailable() && slot.size.equals(parcel.size)) {
                if (slot.tryAssign()) {
                    assignedSlot = slot;
                    break;
                }
            }
        }
        if (assignedSlot != null) {
            System.out.println("Slot " + assignedSlot.slotId + " reserved for order.");
            packageToSlotMapping.put(parcel.id, assignedSlot.slotId);
            NotificationService.sendNotification("Agent assigned for locker: " + selectedLocker.id);
        } else {
            System.out.println("No available slot for this order.");
        }
    }

    public static String getAssignedSlot(String packageId) {
        return packageToSlotMapping.get(packageId);
    }

    public static void removePackageMapping(String packageId) {
        packageToSlotMapping.remove(packageId);
    }
}

class NotificationService {
    public static void sendNotification(String msg) {
        System.out.println("[NotificationService] Sending notification: " + msg);
    }
}

class OTPValidationService {
    private static Map<String, OTPEntry> otpStore = new HashMap<>();
    public static Map<String, String> otpToPackageMapping = new HashMap<>(); // otp -> packageId

    static class OTPEntry {
        String otp;
        long expiryTime;
        OTPEntry(String otp, long expiryTime) {
            this.otp = otp;
            this.expiryTime = expiryTime;
        }
    }

    public static String generateOTP(String packageId) {
        String otp = String.valueOf(new Random().nextInt(900000) + 100000);
        otpStore.put(packageId, new OTPEntry(otp, System.currentTimeMillis() + 300000)); // 5 min expiry
        otpToPackageMapping.put(otp, packageId);
        return otp;
    }

    public static boolean validateOTP(String enteredOTP) {
        // Check if the entered OTP exists in our mapping
        if (!otpToPackageMapping.containsKey(enteredOTP)) {
            return false; // OTP doesn't exist
        }
        
        String packageId = otpToPackageMapping.get(enteredOTP);
        OTPEntry entry = otpStore.get(packageId);
        
        // Validate: OTP matches exactly AND is not expired
        if (entry != null && entry.otp.equals(enteredOTP) && System.currentTimeMillis() < entry.expiryTime) {
            return true;
        }
        
        return false; // Either expired or doesn't match
    }

    public static String getPackageIdFromOTP(String otp) {
        return otpToPackageMapping.get(otp);
    }

    public static void removeOTP(String otp) {
        String packageId = otpToPackageMapping.get(otp);
        if (packageId != null) {
            otpStore.remove(packageId);
            otpToPackageMapping.remove(otp);
        }
    }
}

// ============================ Demo ============================
class AmazonLockerSystem {
    public static void main(String[] args) {
        LockerMachine locker1 = new LockerMachine("L1", "700001");
        LockerMachine locker2 = new LockerMachine("L2", "700003");
        LockerMachine locker3 = new LockerMachine("L3", "700001");
        locker1.slots.add(new Slot("S1", "small"));
        locker1.slots.add(new Slot("S2", "medium"));
        locker2.slots.add(new Slot("S1", "large"));
        locker2.slots.add(new Slot("S2", "medium"));
        locker3.slots.add(new Slot("S1", "small"));
        locker3.slots.add(new Slot("S2", "medium"));

        LockerService lockerService = new LockerService();
        lockerService.addLocker(locker1);
        lockerService.addLocker(locker2);
        lockerService.addLocker(locker3);

        Customer customer = new Customer("Neil", "700001");
        Parcel parcel = new Parcel("P1", "small");

        // ------------------ Customer Order Place Flow ------------------
        LockerMachine selectedLocker = null;
        List<LockerMachine> eligible = lockerService.findEligibleLockers(customer.pincode, parcel.size);
        if (!eligible.isEmpty()) {
            for (LockerMachine locker : eligible) {
                System.out.println("Eligible Locker Found: " + locker.id);
            }
            selectedLocker = eligible.get(0);
            lockerService.placeOrder(customer, parcel, selectedLocker);
        }

        if(selectedLocker == null) {
            System.out.println("No eligible locker found for the order.");
            return;
        }
        
        // ------------------ Agent Delivery Flow ------------------
        selectedLocker.touchScreen();
        selectedLocker.selectMode("Delivery");
        selectedLocker.scanPackage(parcel.id);

        // Get the generated OTP for testing
        String generatedOTP = null;
        for (Map.Entry<String, String> entry : OTPValidationService.otpToPackageMapping.entrySet()) {
            if (entry.getValue().equals(parcel.id)) {
                generatedOTP = entry.getKey();
                break;
            }
        }

        // ------------------ Customer Pickup Flow - Wrong OTP Test ------------------
        System.out.println("\n--- Testing with WRONG OTP ---");
        selectedLocker.touchScreen();
        selectedLocker.selectMode("Pickup");
        selectedLocker.enterOTP("999999"); // Wrong OTP
        
        // ------------------ Customer Pickup Flow - Correct OTP Test ------------------
        System.out.println("\n--- Testing with CORRECT OTP ---");
        selectedLocker.touchScreen();
        selectedLocker.selectMode("Pickup");
        if (generatedOTP != null) {
            System.out.println("Using correct OTP: " + generatedOTP);
            selectedLocker.enterOTP(generatedOTP);
        } else {
            System.out.println("No OTP found for package: " + parcel.id);
        }
        
        // ------------------ Testing Expired OTP (Optional) ------------------
        System.out.println("\n--- Testing with ALREADY USED OTP ---");
        selectedLocker.touchScreen();
        selectedLocker.selectMode("Pickup");
        selectedLocker.enterOTP(generatedOTP); // Try to use same OTP again
    }
}
