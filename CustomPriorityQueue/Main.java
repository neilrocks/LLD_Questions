import java.util.*;
class Main {
    static class Student implements Comparable<Student>{
        int roll_no;
        int ht;
        int wt;
        Student(int roll_no,int ht,int wt){
            this.roll_no = roll_no;
            this.ht=ht;
            this.wt=wt;
        }
        public int compareTo(Student o){
            return this.roll_no-o.roll_no;
        }
        public String toString(){
            return "Roll no "+roll_no+" ht "+ht+" wt "+wt;
        }
    }
    static class StudentHtComparator implements Comparator<Student>{
        public int compare(Student s1,Student s2){
            return s1.ht-s2.ht;
        }
    }
    static class PriorityQueue<T>{
        List<T>data;
        Comparator comp;
        PriorityQueue(){
            data = new ArrayList();
            comp = null;
        }
        PriorityQueue(Comparator comp){
            data = new ArrayList();
            this.comp=comp;
        }
        private void swap(int i,int j){
            T ith = data.get(i);
            T jth = data.get(j);
            data.set(i,jth);
            data.set(j,ith);
        }
        private boolean isSmaller(int i,int j){
            if(comp==null){
                Comparable child = (Comparable)data.get(i);
                Comparable par = (Comparable)data.get(j);
                return child.compareTo(par)<0;
            }else{
                T ith = data.get(i);
                T jth = data.get(j);
                return comp.compare(ith,jth)<0;
            }
        }

        /**
         * Upheapify - Moves an element up the heap to maintain heap property
         * Used after insertion to restore heap order
         * @param i - index of the element to upheapify
         */
        private void upheapify(int i){
            // Base case: if we're at root, stop
            if(i==0) return;
            
            // Calculate parent index
            int pi = (i-1)/2;
            
            // If current element is smaller than parent, swap and continue upward
            if(isSmaller(i,pi)){
                swap(pi,i);
                upheapify(pi);
            }
        }

        /**
         * Downheapify - Moves an element down the heap to maintain heap property
         * Used after removal to restore heap order
         * @param pi - index of the parent element to downheapify
         */
        private void downheapify(int pi){
            // Calculate left child index
            int li = 2*pi+1;
            int mini = pi;
            
            // Check if left child exists and is smaller than current minimum
            if(li<data.size()&&isSmaller(li,mini)){
                mini=li;
            }
            
            // Calculate right child index
            int ri = 2*pi+2;
            
            // Check if right child exists and is smaller than current minimum
            if(ri<data.size()&&isSmaller(ri,mini)){
                mini=ri;
            }
            
            // If a child is smaller than parent, swap and continue downward
            if(mini!=pi){
                swap(pi,mini);
                downheapify(mini);
            }
        }

        void add(T val){
            data.add(val);
            //upheapify(data.size()-1);
			//efficient construct
			for(int i = data.size()/2-1;i>=0;i--){
				downheapify(i);
				
			}
        }
        T peek(){
            if(this.size()==0){
                System.out.println("Queue underflow");
                return null;
            }
            return data.get(0);
        }
        T remove(){
             if(this.size()==0){
                System.out.println("Queue underflow");
                return null;
            }
            swap(0,data.size()-1);
            T val = data.remove(data.size()-1);
            downheapify(0);
            return val;
        }
        int size(){
            return data.size();
        }
    }
    public static void main(String[] args) {
        PriorityQueue<Student> p = new PriorityQueue<>(new StudentHtComparator());
        p.add(new Student(10,180,80));
        p.add(new Student(5,160,90));
        p.add(new Student(20,170,77));
        p.add(new Student(1,100,60));
        p.add(new Student(15,90,40));
        // System.out.println(p.peek());
        // System.out.println(p.remove());
        // System.out.println(p.remove());
        // System.out.println(p.peek());
        // System.out.println(p.remove());
        // System.out.println(p.remove());
        // System.out.println(p.remove());
        // System.out.println(p.remove());
        while(p.size()!=0){
            System.out.println(p.remove());
        }
    }
}