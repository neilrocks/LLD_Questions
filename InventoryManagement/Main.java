import java.util.*;

// ---------------- CATEGORY ----------------
enum CATEGORY {
    ELECTRONIC, CLOTHES, MAKEUP
}

// ---------------- PRODUCT ----------------
class Product {
    private final String sku;     // unique id per product type
    private final CATEGORY category;
    private final String name;
    private int qty;
    private final int price;

    public Product(String sku, CATEGORY category, String name, int price, int qty) {
        this.sku = sku;
        this.category = category;
        this.name = name;
        this.price = price;
        this.qty = qty;
    }

    public String getSku() { return sku; }
    public CATEGORY getCategory() { return category; }
    public String getName() { return name; }
    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }
    public int getPrice() { return price; }

    @Override
    public String toString() {
        return name + "(" + sku + ") qty=" + qty;
    }
}

// ---------------- OBSERVER ----------------
interface Observer {
    // include warehouseId so observers know where the event originated
    void update(Product product, String warehouseId, String message);
}

class EmailNotifier implements Observer {
    @Override
    public void update(Product product, String warehouseId, String message) {
        System.out.println("[EMAIL][" + warehouseId + "] " + message + " -> " + product);
    }
}

class SmsNotifier implements Observer {
    @Override
    public void update(Product product, String warehouseId, String message) {
        System.out.println("[SMS][" + warehouseId + "] " + message + " -> " + product);
    }
}

// ---------------- NOTIFICATION SERVICE ----------------
class NotificationService {
    private final List<Observer> observers = new ArrayList<>();

    public void registerObserver(Observer observer) {
        observers.add(observer);
    }

    public void notifyObserverForLowStock(Product product, String warehouseId) {
        notifyAll(product, warehouseId, "Product quantity low");
    }

    public void notifyObserverForRefillDone(Product product, String warehouseId) {
        notifyAll(product, warehouseId, "Product refilled");
    }

    private void notifyAll(Product product, String warehouseId, String message) {
        for (Observer obs : observers) {
            obs.update(product, warehouseId, message);
        }
    }
}

// ---------------- STRATEGY (Refill) ----------------
interface RefillStrategy {
    void refill(Product product);
}

class FixedRefillStrategy implements RefillStrategy {
    private final int refillAmount;
    public FixedRefillStrategy(int refillAmount) { this.refillAmount = refillAmount; }
    @Override
    public void refill(Product product) {
        product.setQty(product.getQty() + refillAmount);
        System.out.println("RefillStrategy: added " + refillAmount + " to " + product.getName());
    }
}

// ---------------- WAREHOUSE ----------------
class Warehouse {
    private final String id;
    private final Map<String, Product> productMap = new HashMap<>();

    public Warehouse(String id) { this.id = id; }
    public String getId() { return id; }

    public void addProduct(Product product) {
        productMap.put(product.getSku(), product);
    }

    // returns true if removal succeeded
    public boolean removeProduct(String sku, int qty) {
        Product p = productMap.get(sku);
        if (p == null) {
            System.out.println("[" + id + "] Product " + sku + " not found.");
            return false;
        }
        if (p.getQty() < qty) {
            System.out.println("[" + id + "] Insufficient stock for " + p.getName());
            return false;
        }
        p.setQty(p.getQty() - qty);
        System.out.println("[" + id + "] Removed " + qty + " of " + p.getName());
        return true;
    }

    public void updateProduct(String sku, int qty) {
        Product p = productMap.get(sku);
        if (p != null) {
            p.setQty(qty);
            System.out.println("[" + id + "] Updated " + p.getName() + " to qty " + qty);
        } else {
            System.out.println("[" + id + "] Product " + sku + " not found.");
        }
    }

    public Product getProduct(String sku) {
        return productMap.get(sku);
    }
}

// ---------------- INVENTORY MANAGER (multi-warehouse) ----------------
class InventoryManager {
    private static final int LOW_THRESHOLD = 5; // configurable constant
    private static InventoryManager instance;

    private final Map<String, Warehouse> warehouses = new HashMap<>();
    private final NotificationService notificationService = new NotificationService();
    private RefillStrategy refillStrategy;

    private InventoryManager() {}

    public static synchronized InventoryManager getInstance() {
        if (instance == null) instance = new InventoryManager();
        return instance;
    }

    public NotificationService getNotificationService() { return notificationService; }

    public void setRefillStrategy(RefillStrategy strategy) { this.refillStrategy = strategy; }

    public void addWarehouse(String warehouseId) {
        warehouses.put(warehouseId, new Warehouse(warehouseId));
        System.out.println("Added warehouse: " + warehouseId);
    }

    public void addProduct(String warehouseId, String sku, CATEGORY category, String name, int price, int qty) {
        Warehouse wh = warehouses.get(warehouseId);
        if (wh == null) {
            System.out.println("Warehouse " + warehouseId + " not found.");
            return;
        }
        Product p = new Product(sku, category, name, price, qty);
        wh.addProduct(p);
        System.out.println("Added product " + p + " to " + warehouseId);
    }

    public void remove(String warehouseId, String sku, int qty) {
        Warehouse wh = warehouses.get(warehouseId);
        if (wh == null) {
            System.out.println("Warehouse " + warehouseId + " not found.");
            return;
        }

        boolean ok = wh.removeProduct(sku, qty);
        if (!ok) return;

        Product p = wh.getProduct(sku);
        if (p != null && p.getQty() < LOW_THRESHOLD && refillStrategy != null) {
            notificationService.notifyObserverForLowStock(p, warehouseId);
            refillStrategy.refill(p);
            notificationService.notifyObserverForRefillDone(p, warehouseId);
        }
    }

    public void update(String warehouseId, String sku, int qty) {
        Warehouse wh = warehouses.get(warehouseId);
        if (wh == null) {
            System.out.println("Warehouse " + warehouseId + " not found.");
            return;
        }
        wh.updateProduct(sku, qty);

        Product p = wh.getProduct(sku);
        if (p != null && p.getQty() < LOW_THRESHOLD && refillStrategy != null) {
            notificationService.notifyObserverForLowStock(p, warehouseId);
            refillStrategy.refill(p);
            notificationService.notifyObserverForRefillDone(p, warehouseId);
        }
    }

    // helper: check availability across all warehouses (simple example)
    public Optional<String> findWarehouseWithStock(String sku, int requiredQty) {
        for (Map.Entry<String, Warehouse> e : warehouses.entrySet()) {
            Product p = e.getValue().getProduct(sku);
            if (p != null && p.getQty() >= requiredQty) return Optional.of(e.getKey());
        }
        return Optional.empty();
    }
}

// ---------------- MAIN DEMO ----------------
public class Main {
    public static void main(String[] args) {
        InventoryManager mgr = InventoryManager.getInstance();

        // Setup warehouses
        mgr.addWarehouse("Delhi");
        mgr.addWarehouse("Kolkata");

        // Setup notifications
        mgr.getNotificationService().registerObserver(new EmailNotifier());
        mgr.getNotificationService().registerObserver(new SmsNotifier());

        // Set refill policy
        mgr.setRefillStrategy(new FixedRefillStrategy(10));

        // Add products to different warehouses
        mgr.addProduct("Delhi", "SKU-101", CATEGORY.ELECTRONIC, "Laptop", 1000, 10);
        mgr.addProduct("Kolkata", "SKU-102", CATEGORY.CLOTHES, "T-Shirt", 20, 15);
        mgr.addProduct("Kolkata", "SKU-103", CATEGORY.MAKEUP, "Lipstick", 15, 3);

        // Operations that trigger notifications/refill
        mgr.remove("Delhi", "SKU-101", 7);      // laptop left 3 -> low -> refill
        mgr.remove("Kolkata", "SKU-103", 1);    // lipstick left 2 -> low -> refill
        mgr.update("Kolkata", "SKU-102", 2);    // tshirt updated to 2 -> low -> refill

        // Query example
        Optional<String> withStock = mgr.findWarehouseWithStock("SKU-101", 5);
        System.out.println("Warehouse with required stock for SKU-101: " + withStock.orElse("none"));
    }
}
/*“Currently, all operations are single-threaded. In a concurrent environment, we would need 
to synchronize access to the warehouse product map and product quantities. 
For fine-grained concurrency, we can lock at the warehouse 
or product level. Additionally, the NotificationService could be made async so notifications don’t block inventory updates.” */