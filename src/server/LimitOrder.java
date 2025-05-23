public class LimitOrder extends Order{
    public final int limitPrice;

    public LimitOrder(Type type, int size, User u, int limitPrice){
        super(type, size, u);
        this.limitPrice = limitPrice;
    }

    public int getLimitPrice() { return limitPrice; }

}
