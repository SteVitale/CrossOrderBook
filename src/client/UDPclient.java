import com.google.gson.*;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

/* Classe per creare un thread listener delle notifiche udp */
public class UDPclient implements Runnable {
    private DatagramSocket udp;
    private int port; //porta udp su cui l'utente si mette in ascolto
    private volatile boolean running = true;

    public UDPclient(int port) {
        this.port = port;
    }

    @Override
    public void run(){
        try {
            udp = new DatagramSocket(this.port);
            System.out.println("In ascolto per notifiche UDP sulla porta " + this.port);

            while(running) { // finché il client è attivo, il thread ascolta notifiche asincrone
                byte[] buffer = new byte[512];

                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udp.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                printNotif(message);//gestisco il messaggio ricevuto dal server
            }
        } catch(SocketException se){
               // System.err.println("Errore di socket UDP: "+se.getMessage());
        } catch (Exception e) {
                System.err.println("Errore nella ricezione delle notifiche: " + e.getMessage());
        } finally {
            if (udp != null && !udp.isClosed()) {
                udp.close();
            }
        }
    }

    private void printNotif(String message){
        try{
            Gson gson = new Gson();
            JsonObject obj = gson.fromJson(message, JsonObject.class);
            String notifType = obj.get("notification").getAsString();

            System.out.println("-----------------------------------------------------");
            System.out.println("-- Notifica ricevuta -- "+ notifType);

            if(notifType.equals("closedTrades")){
                JsonArray trades = obj.getAsJsonArray("trades");

                for(JsonElement elem : trades){
                    JsonObject trade = elem.getAsJsonObject();
                    int orderId = trade.get("orderId").getAsInt();
                    String type = trade.get("type").getAsString();
                    String orderType = trade.get("orderType").getAsString();
                    int size = trade.get("size").getAsInt();
                    int price = trade.get("price").getAsInt();
                    long timestamp = trade.get("timestamp").getAsLong();


                    System.out.println("Trade: orderId = " + orderId +
                            ", type = " + type +
                            ", orderType = " + orderType +
                            ", size = " + size +
                            ", price = " + price +
                            ", timestamp = " + timestamp);
                }
            }else {
                System.out.println("Notifica sconosciuta: " + message);
            }
            System.out.println("-----------------------------------------------------");

        }catch(JsonSyntaxException jse){
            System.err.println("Errore parsin json: "+jse.getMessage());
        }
    }

    public void close(){
        running = false;
        if(udp != null && !udp.isClosed())
            udp.close();
    }

}
