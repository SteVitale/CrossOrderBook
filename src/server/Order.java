public abstract class Order {
    public enum Type { ASK, BID }; //Tipi per distinguere gli ordini
    private int ID = -1; //ID default in caso di errore
    private User user; //Riferimento all'utente che effettua l'ordine
    private final Type type; //Tipo dell'ordine corrente

    private int size_iniziale; //size con cui viene effettuato l'ordine
    private int size; //copia della size iniziale su cui effettuare calcoli di residui

    private long timestamp; //per garantire fair matching

    public Order(Type type, int size, User u){
        this.type = type;
        this.size_iniziale = size;
        this.size = size;
        this.timestamp = System.currentTimeMillis() / 1000; //converto in secondi
        this.user = u;
    }

    public void setId(int id) { //lo cambio solo se è uguale a -1 (mai cambiato)
        if(this.ID == -1){
            this.ID = id;
        }

    }
    public int getId() { return this.ID; }
    public User getUser() { return this.user; }
    public Type getType() { return type; }
    public String getStrType() { return type == Type.BID ? "bid":"ask"; }
    public int getSize() { return size; }
    public long getTimestamp() { return timestamp; }
    public void setSize(int size) { this.size = size; }


    // "ask/bid" -> Type.ASK/BID
    public static Type StringToType(String type){
        type = type.toUpperCase();

        if(type.equals("BID"))
            return Type.BID;
        else if(type.equals("ASK"))
            return Type.ASK;
        else
            return null;
    }
}
