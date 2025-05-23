import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/*

 CLASSE PER INCAPSULARE LE OPERAZIONI DI
 APERTURA / SCRITTURA / LETTURA / CHIUSURA DELLA SOCKET CLIENT

 */

public class ClientSocket {
    private final String serverAddress; //Indirizzo Server
    private final int port;//Porta Server
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;
    private Gson gson;


    public ClientSocket(String serverAddress, int port) {
        this.serverAddress = serverAddress;
        this.port = port;
        this.gson = new Gson();
    }

    public void connect() throws IOException {
        socket = new Socket(serverAddress, port); //socket per la comunicazione
        writer = new PrintWriter(socket.getOutputStream(), true); //flusso in uscita
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream())); //flusso in entrata

        System.out.println("Connessione al server ["+serverAddress+":"+port+"] riuscita.");
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void disconnect() {
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Errore durante la disconnessione: " + e.getMessage());
        }
    }

    public void sendRequest(JsonObject request) throws IOException {
        if (!isConnected()) {
            throw new IOException("Connessione1 non attiva. Impossibile inviare la richiesta.");
        }
        try {
            writer.println(gson.toJson(request)); // SERIALIZZAZIONE + Invia il messaggio al server
        } catch (Exception e) {
            throw new IOException("Errore durante l'invio della richiesta: " + e.getMessage(), e);
        }
    }

    public JsonObject receiveResponse() throws IOException {
        if (!isConnected()) {
            throw new IOException("Connessione non attiva. Impossibile ricevere la risposta.");
        }
        try {
            String jsonResponse = reader.readLine(); //LEGGE IL MESSAGGIO DI RISPOSTA
            JsonObject jo = gson.fromJson(jsonResponse, JsonObject.class); //DESERIALIZZAZIONE
            return jo; //restituisci al client l'oggetto risposta
        } catch (IOException e) {
             disconnect();
             throw e;
        }
    }

    public void waitForReconnect(int reconnect_delay) { //chiamata dal MainClient per impostare un delay
        try {
            Thread.sleep(reconnect_delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
