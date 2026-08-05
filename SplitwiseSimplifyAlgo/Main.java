class Main{
    public int countMinTrax(int[][]trax){
        int n=trax.length;
        Map<Integer,Integer> map = new HashMap<>();
        for(int i=0;i<n;i++){
            int from = trax[i][0];
            int to = trax[i][1];
            int amount = trax[i][2];
            map.put(from,map.getOrDefault(from,0)-amount);
            map.put(to,map.getOrDefault(to,0)+amount);
        }
        List<Integer>balances = new ArrayList<>();
        for(int balance:map.values()){
            if(balance!=0){//no need to add 0 balance
                balances.add(balance);
            }
        }
        int minTrax=dfs(balances,0);
        return minTrax;
    }
    public int dfs(List<Integer>balances,int idx){
        if(balances.size()==0||idx>=balances.size()){
            return 0;
        }
        if(balances.get(idx)==0){
            return dfs(balances,idx+1);//skip the zeros
        }
        int minTrax=Integer.MAX_VALUE;
        for(int i=idx+1;i<balances.size();i++){
            if(balances.get(idx)*balances.get(i)<0){//opposite sign
                balances.set(i,balances.get(i)+balances.get(idx));
                minTrax=Math.min(minTrax,1+dfs(balances,idx+1));
                balances.set(i,balances.get(i)-balances.get(idx));//backtrack
                // pruning
                if (balances.get(i) + balances.get(idx) == 0)
                    break;
                }
           
        }
        return minTrax==Integer.MAX_VALUE?0:minTrax;
    }
    public static void main(String[] args) {
        System.out.println(countMinTrax(trax));
    }
}