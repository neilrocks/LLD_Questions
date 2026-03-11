// Online Java Compiler
// Use this editor to write, compile and run your Java code online
import java.util.*;

class Main {
    // Custom HashMap implementation using separate chaining for collision handling
    static class HashMap<K,V>{
        
        // Node class to store key-value pairs in the linked list
        class HMNode{
            K key;
            V val;
            HMNode(K key,V val){
                this.key = key;
                this.val = val;
            }
        }
        
        // Array of linked lists - each bucket contains a linked list to handle collisions
        LinkedList<HMNode> []buckets;
        // Total number of key-value pairs in the hashmap
        int size;
        
        // Constructor - initialize with 4 buckets by default
        HashMap(){
            initBuckets(4);
            size = 0;
        }
        
        // Helper method to create buckets array and initialize each bucket with empty linked list
        private void initBuckets(int n){
            buckets = new LinkedList[n];
            for(int bi=0;bi<n;bi++){
                buckets[bi]=new LinkedList();
            }
        }
        
        // Hash function - converts key to bucket index using hashCode and modulo operation
        // Math.abs ensures we get positive index
        private int hashFunc(K key){
            return Math.abs(key.hashCode())%buckets.length;
        }
        
        // Find the position of a key within a specific bucket's linked list
        // Returns index if found, -1 if not found
        private int getDataIndexWithinTheBucket(K key,int bi){
            int idx = 0;
            for(HMNode node:buckets[bi]){
                if(node.key.equals(key)) return idx;
                idx++;
            }
            return -1;
        }
        
        // Insert or update key-value pair
        public void put(K key,V val){
            // Step 1: Find which bucket this key belongs to
            int bi = hashFunc(key);
            // Step 2: Check if key already exists in that bucket
            int di = getDataIndexWithinTheBucket(key,bi);
            
            if(di!=-1){
                // Key exists - update the value
                HMNode node = buckets[bi].get(di);
                node.val = val;
            }else{
                // Key doesn't exist - add new node to the bucket
                buckets[bi].add(new HMNode(key,val));
                size++;
            }
            
            // Step 3: Check load factor (lambda = n/N where n=size, N=buckets)
            // If lambda > 2, rehash to maintain O(1) average time complexity
            double lambda = size*1.0/buckets.length;
            if(lambda>2){
                rehash();
            }
        }
        
        // Rehashing - double the bucket size and redistribute all elements
        // This maintains O(1) average time complexity by keeping load factor low
        public void rehash(){
            // Step 1: Store reference to old buckets array
            LinkedList<HMNode>[] oba = buckets;
            // Step 2: Create new buckets array with double the size
            initBuckets(2*buckets.length);
            size = 0;
            // Step 3: Reinsert all old elements into new buckets
            for(int i =0;i<oba.length;i++){
                for(HMNode node:oba[i]){
                    put(node.key,node.val);
                }
            }
        }
        
        // Check if a key exists in the hashmap
        public boolean containsKey(K key){
            int bi = hashFunc(key);
            int di = getDataIndexWithinTheBucket(key,bi);
            if(di!=-1){
                return true;
            }
            return false;
        }
        
        // Get value associated with a key
        public V get(K key){
            if(size==0){
                System.out.println("Empty hashmap ");
                return null;
            }
            // Step 1: Find the bucket
            int bi = hashFunc(key);
            // Step 2: Find the data index within that bucket
            int di = getDataIndexWithinTheBucket(key,bi);
            
            if(di!=-1){
                // Key found - return the value
                HMNode node = buckets[bi].get(di);
                return node.val;
            }else{
                // Key not found
                System.out.println("Key "+key+" not present in map");
                return null;
            }
        }
        
        // Remove a key-value pair and return the value
        public V remove(K key){
            if(size==0){
                System.out.println("Empty hashmap ");
                return null;
            }
            // Step 1: Find the bucket
            int bi = hashFunc(key);
            // Step 2: Find the data index within that bucket
            int di = getDataIndexWithinTheBucket(key,bi);
            
            if(di!=-1){
                // Key found - remove from linked list and decrease size
                HMNode node = buckets[bi].get(di);
                buckets[bi].remove(di);
                size--;
                return node.val;
            }else{
                // Key not found
                System.out.println("Key "+key+" not present in map");
                return null;
            }
        }
        
        // Return all keys in the hashmap as a Set
        public Set<K> keySet(){
            Set<K> set = new HashSet();
            // Iterate through all buckets and all nodes in each bucket
            for(int i = 0;i<buckets.length;i++){
                for(HMNode node:buckets[i]){
                    set.add(node.key);
                }
            }
            return set;
        }
    }
    
    // Test the custom HashMap implementation
    public static void main(String[] args) {
        HashMap<String,Integer>map = new HashMap();
        // Insert multiple key-value pairs
        map.put("India",100);
        map.put("America",500);
        map.put("Canada",700);
        map.put("China",900);
        map.put("Australia",1000);
        
        // Print all keys
        for(String val:map.keySet()){
            System.out.print(val+ ",");
        }
        System.out.println();
        
        // Test remove operation
        System.out.println(map.remove("India"));
        // Test get operation
        System.out.println(map.get("Canada"));
    }
}