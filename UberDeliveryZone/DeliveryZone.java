import java.util.Arrays;

class DSU{
    int[]par;
    int[]size;
    int zoneCount;
    DSU(int n){
        zoneCount=0;
        par=new int[n];
        size=new int[n];
        Arrays.fill(size,1);
        for(int i=0;i<n;i++)par[i]=i;
    }
    public int findPar(int x){
        return par[x] != x ? (par[x] = findPar(par[x])) : x;

    }
    public void union(int u,int v){
        int lx=findPar(u),ly=findPar(v);
        if(lx!=ly){
            if(size[lx]<size[ly]){
                size[ly]+=size[lx];
                par[lx]=ly;
            }else{
                size[lx]+=size[ly];
                par[ly]=lx;
            }
            zoneCount--;
        }
    }
}
class DeliveryZoneSystem{
    int n;
    int m;
    int[][]grid;
    DSU ds;
    int[][]dir={{1,0},{-1,0},{0,1},{0,-1}};
    DeliveryZoneSystem(int n,int m){
        this.n=n;
        this.m=m;
        grid=new int[n][m];
        ds=new DSU(n*m);
    }
    private int getCellNo(int r,int c){
        return r*m+c;
    }
    public void openRestaurant(int r,int c){
        if (grid[r][c] == 1) return; // already open
        grid[r][c]=1;//open
        ds.zoneCount++;
        for(int[]d:dir){
            int x=r+d[0],y=c+d[1];
            if(x<0||y<0||x>=n||y>=m||!hasOpenRestaurant(x,y))continue;
            int cell_no=getCellNo(r,c);
            int nbr_cell_no=getCellNo(x,y);
            ds.union(cell_no,nbr_cell_no);
        }
    }
    public int countOpenRestaurant(int r,int c){
        int cell_no=getCellNo(r,c);
        int parent = ds.findPar(cell_no);
        return ds.size[parent];

    }
    public boolean hasOpenRestaurant(int r,int c){
        return grid[r][c]==1;
    }
    public int countDeliveryZones(){
        return ds.zoneCount;
    }
}
class Main {
    public static void main(String[] args) {
        DeliveryZoneSystem sys=new DeliveryZoneSystem(3,3);
        sys.openRestaurant(0, 0);
        sys.openRestaurant(0, 1);
        sys.openRestaurant(1, 2);
        System.out.println(sys.countDeliveryZones()); // 2
        sys.openRestaurant(0, 2);
        System.out.println(sys.countDeliveryZones());//1
        
    }
}