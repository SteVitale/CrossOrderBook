public class StopOrder extends Order{
    private final int stopPrice;

    public StopOrder(Type type, int size, User u, int stopPrice){
        super(type, size, u);
        this.stopPrice = stopPrice;
    }

    public int getStopPrice() { return stopPrice; }

}
