public class MarketOrder extends Order {
    private boolean fromStop; //flag per segnalare se il market order deriva da uno stop order sbloccato

    public MarketOrder(Type type, int size, User u){
        super(type, size, u);
        fromStop = false;
    }

    public MarketOrder(Type type, int size, User u, boolean isFromStop){
        super(type, size, u);
        fromStop = true;
    }

    public boolean isFromStop(){
        return fromStop;
    }


}
