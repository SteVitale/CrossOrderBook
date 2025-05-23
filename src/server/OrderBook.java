import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OrderBook {
    //ORDER DATA
    private ConcurrentHashMap<Integer, Order> orderIdMap; //Mappa per associare un id ad ogni ordine

    private PriorityQueue<LimitOrder> bidOrders; //Ordini di acquisto
    private PriorityQueue<LimitOrder> askOrders; //Ordini di vendita

    private List<StopOrder> stopOrders; //Stop Order in attesa di essere attivati

    //SHARED DATA
    private final UserManager userManager;

    private int id_counter = 0;

    public OrderBook(UserManager um){
        bidOrders = new PriorityQueue<>((a, b) -> { //il primo limit price bid è il piu alto
            if(a.getLimitPrice() == b.getLimitPrice())
                return Long.compare(a.getTimestamp(), b.getTimestamp());
            else return Integer.compare(b.getLimitPrice(), a.getLimitPrice());
        });

        askOrders = new PriorityQueue<>((a, b) -> { //il primo limit price ask è il più basso
            if(a.getLimitPrice() == b.getLimitPrice())
                return Long.compare(a.getTimestamp(), b.getTimestamp());
            else return Integer.compare(a.getLimitPrice(), b.getLimitPrice());
        });

        orderIdMap = new ConcurrentHashMap<>();
        stopOrders = new ArrayList<>();
        userManager = um;
    }

    public synchronized int addOrder(Order order){
        boolean check = false; //variabile per indicare la corretta creazione/gestione dell'ordine

        //AGGIORNAMENTO ID ORDINI
        id_counter++;
        order.setId(id_counter);
        orderIdMap.put(id_counter, order);

        //RECUPERO IL TIPO DI ORDINE E LO MANDO AL RISPETTIVO HANDLER
        if(order instanceof MarketOrder){

            //gestisco i market e ritorno successo/fallimento
            check = executeMarketOrder((MarketOrder) order);

        } else if(order instanceof StopOrder){

            //aggiungo stopOrder alla lista (uno stopOrder non può sbloccare altri StopOrder)
            stopOrders.add((StopOrder) order);
            check = true; //Va sempre a buon fine

        } else if(order instanceof LimitOrder){

            //gestisco i limit e ritorno successo/fallimento
            check = executeLimitOrder((LimitOrder) order);

        } else {
            check = false;
        }

        if(!check){
            //rumovo l'ordine e decremento il counter
            orderIdMap.remove(id_counter);
            id_counter--;
            return -1;
        }
        /* provo a sbloccare gli stopOrder in attesa */
        executeStopOrder();
        return id_counter;
    }

    //FUNZIONE DI UTILITY ID -> ORDER
    public Order getOrderById(int id) {
        return orderIdMap.get(id);
    }

    public synchronized boolean removeOrder(int id, String user) {
        Order o = getOrderById(id);
        if(o == null || !o.getUser().getUsername().equals(user)){
            return false;
        }
        if (orderIdMap.containsKey(id)) {
            if(o instanceof LimitOrder){
                if(o.getType() == Order.Type.BID)
                    bidOrders.remove(o);
                else
                    askOrders.remove(o);
            } else if(o instanceof StopOrder){
                stopOrders.remove(o);
            }

            orderIdMap.remove(id);
            System.out.println("Ordine ["+id+"] cancellato correttamente.");
            return true;
        }
        System.out.println("Ordine ["+id+"] non trovato.");
        return false;
    }

    public synchronized boolean executeLimitOrder(LimitOrder lo){
        PriorityQueue<LimitOrder> opposite = lo.getType() == Order.Type.BID ? askOrders : bidOrders;
        List<JsonObject> trade_utente = new ArrayList<>();

        while(!opposite.isEmpty() && lo.getSize() > 0){
            LimitOrder topOrder = opposite.peek(); //ask (vendita) con il limit price più piccolo
            boolean checkBid =  (lo.getType() == Order.Type.BID) && (lo.getLimitPrice() >= topOrder.getLimitPrice());
            boolean checkAsk =  (lo.getType() == Order.Type.ASK) && (lo.getLimitPrice() <= topOrder.getLimitPrice());

            if(checkBid || checkAsk) {
                int evadedSize = Math.min(lo.getSize(), topOrder.getSize());
                int price = topOrder.getLimitPrice();

                if(lo.getSize() >= topOrder.getSize()){
                    lo.setSize(lo.getSize() - topOrder.getSize());
                    opposite.poll();
                } else {
                    topOrder.setSize(topOrder.getSize() - lo.getSize());
                    lo.setSize(0);
                }

                JsonObject trade1 = saveTransaction(topOrder, price, evadedSize);
                JsonObject trade2 = saveTransaction(lo, price, evadedSize);

                if(topOrder.getUser().getUsername().equals(lo.getUser().getUsername())){
                    trade_utente.add(trade1);
                    trade_utente.add(trade2);
                }else{
                    sendTradeNotification(topOrder.getUser().getUsername(), Arrays.asList(trade1));
                    sendTradeNotification(lo.getUser().getUsername(), Arrays.asList(trade2));
                }

            }else{
                break; //se il migliore degli opposite non soddisfa la condizione è inutile andare avanti
            }
        }

        if(!trade_utente.isEmpty()){
            sendTradeNotification(lo.getUser().getUsername(), trade_utente);
        }

        if(lo.getSize() > 0){
            (lo.getType() == Order.Type.ASK ? askOrders : bidOrders).add(lo);
        }
        return true;
    }

    public synchronized boolean executeMarketOrder(MarketOrder mo){
        PriorityQueue<LimitOrder> opposite = (mo.getType() == Order.Type.BID) ? askOrders : bidOrders;
        List<JsonObject> trade_utente = new ArrayList<>();
        int remainingSize = mo.getSize(); //qt cripto da acquistare / vendere rimanente

        int sum = 0;
        for(LimitOrder o : opposite){ //da testare
            sum += o.getSize();
        }

        if(mo.getSize() <= sum){ //ci assicura che negli opposite c'è abbastanza size per evadere
            while(remainingSize > 0){
                LimitOrder topOrder = (LimitOrder) opposite.peek();
                int evadedSize = Math.min(remainingSize, topOrder.getSize());
                int price = topOrder.getLimitPrice();

                if(remainingSize >= topOrder.getSize()){
                    remainingSize -= topOrder.getSize();
                    opposite.poll();
                } else {
                    topOrder.setSize(topOrder.getSize() - remainingSize);
                    remainingSize = 0;
                }
                // Salva la transazione per entrambi gli ordini.
                JsonObject tradeOpp = saveTransaction(topOrder, price,evadedSize);
                JsonObject tradeMo  = saveTransaction(mo, price,evadedSize);

                // Se gli ordini appartengono allo stesso utente, aggrega i trade.
                if (topOrder.getUser().getUsername().equals(mo.getUser().getUsername())) {
                    trade_utente.add(tradeOpp);
                    trade_utente.add(tradeMo);
                } else {
                    sendTradeNotification(topOrder.getUser().getUsername(), Arrays.asList(tradeOpp));
                    sendTradeNotification(mo.getUser().getUsername(), Arrays.asList(tradeMo));
                }
            }

            if (!trade_utente.isEmpty()) {
                sendTradeNotification(mo.getUser().getUsername(), trade_utente);
            }
            return true;
        } else {
            return false;
        }
    }


    public synchronized void executeStopOrder(){ //tentativo di evadere stop orders
        Iterator<StopOrder> it = stopOrders.iterator();
        while (it.hasNext()) {
            StopOrder so = it.next();
            if (so.getType() == Order.Type.ASK) {
                if (bidOrders.isEmpty()) continue; // evita NullPointerException
                int bestBid = bidOrders.peek().getLimitPrice();

                if (so.getStopPrice() >= bestBid) {
                    // usando il secondo costruttore della classe MarketOrder posso specificare isFromStop = true
                    MarketOrder mo = new MarketOrder(so.getType(), so.getSize(), so.getUser(), true);

                    mo.setId(so.getId()); //copio l'id nel market-order derivato dallo stop-order
                    executeMarketOrder(mo);
                    it.remove();
                }
            } else if (so.getType() == Order.Type.BID) {
                if (askOrders.isEmpty()) continue; // evita NullPointerException
                int bestAsk = askOrders.peek().getLimitPrice();

                if (so.getStopPrice() <= bestAsk) {
                    // usando il secondo costruttore della classe MarketOrder posso specificare isFromStop = true
                    MarketOrder mo = new MarketOrder(so.getType(), so.getSize(), so.getUser(), true);

                    mo.setId(so.getId()); //copio l'id nel market-order derivato dallo stop-order
                    executeMarketOrder(mo);
                    it.remove();
                }
            }
        }
    }

    //SALVATAGGIO DEGLI ORDINI EVASI CORRETTAMENTE
    public JsonObject saveTransaction(Order order, int price, int size_evasa) {
        Gson gson = new Gson();//Builder().setPrettyPrinting().create();
        JsonObject record = new JsonObject();

        // Determinazione del tipo di ordine
        String orderType;
        if (order instanceof MarketOrder) {
            if (((MarketOrder)order).isFromStop())
                orderType = "stop";
            else
                orderType = "market";
        } else if (order instanceof LimitOrder)
            orderType = "limit";
        else
            orderType = "error";

        record.addProperty("orderId", order.getId());
        record.addProperty("type", order.getStrType());
        record.addProperty("orderType", orderType);
        record.addProperty("size", size_evasa);  //SIZE usata per evadere l'ordine
        record.addProperty("price", price);
        record.addProperty("timestamp", order.getTimestamp());

        //salvataggio del nuovo record nell'array "trades" del file json configurato
        synchronized (MainServer.transactionsFileLock) {
            try (RandomAccessFile raf = new RandomAccessFile(MainServer.TRANSACTIONS_FILE, "rw")){
                long length = raf.length();
                if (length == 0) {
                    // File vuoto: crea array con primo elemento
                    String init = "{\n  \"trades\": [\n    "
                            + gson.toJson(record)
                            + "\n  ]\n}";
                    raf.setLength(0);
                    raf.write(init.getBytes());
                } else {
                    // Cerca la chiusura dell'array: la sequenza "\n  ]"
                    byte[] buf = new byte[(int) length];
                    raf.readFully(buf);
                    String content = new String(buf);
                    int idx = content.lastIndexOf("\n  ]");
                    if (idx < 0) {
                        throw new IOException("Formato JSON invalido: chiusura array non trovata");
                    }
                    long pos = idx + 1; // include '\n'
                    raf.setLength(pos);
                    raf.seek(pos);
                    // Aggiunge record separato da virgola
                    String entry = "  ,\n    " + gson.toJson(record) + "\n  ]\n}";
                    raf.write(entry.getBytes());
                }
            } catch (IOException e) {
                System.err.println("Errore salvataggio ordine: " + e.getMessage());
            }
        }
        return record;
    }

    //Invio agli utenti interessati la notifica degli ordini evasi
    private void sendTradeNotification(String username, List<JsonObject> trades)  {
        /* RECUPERO IP E PORTA UTENTE */
        int udpPort = userManager.getUdpPort(username);
        if(udpPort <= 0) return;
        InetAddress ip = userManager.getUserInetAddress(username);

        if (udpPort != -1 && ip != null) {
            JsonObject notification = new JsonObject();
            notification.addProperty("notification", "closedTrades");

            JsonArray tradesArray = new JsonArray();

            /* AGGIUNGO TUTTI GLI ORDINI, EFFETTUATI DALL'UTENTE CORRENTE, CHE SONO STATI EVASI */
            for (JsonObject trade : trades) {
                tradesArray.add(trade);
            }

            notification.add("trades", tradesArray);
            Gson gson = new Gson();
            new UDPserver(ip, udpPort).send(gson.toJson(notification));
        }
    }

}
