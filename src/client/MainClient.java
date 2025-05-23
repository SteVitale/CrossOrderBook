import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Scanner;

public class MainClient {

    /* CONFIG DATA */
    private static LoaderConfig config = null;
    private static String SERVER_ADDRESS = null;
    private static int SERVER_PORT;
    private static int UDP_PORT;
    private static int RECONNECTION_DELAY;

    /* CONNECTION DATA */
    private static ClientSocket clientSocket = null;
    private static UDPclient listenerSocket = null;

    /* SYNC DATA */
    private static boolean isRunning;
    private static Thread listenerThread = null;

    /* USER DATA */
    private static Scanner userScan = null;
    private static boolean loggedIn = false;


    public static void main(String[] args) {
        isRunning = true;
        import_config();

        while(isRunning){
            try {
                /* CREAZIONE LISTENER UDP */
                listenerSocket = new UDPclient(UDP_PORT);
                startNotificationListener();

                /* AVVIO CONNESSIONE TCP */
                clientSocket = new ClientSocket(SERVER_ADDRESS, SERVER_PORT);

                /* TENTO IN LOOP LA CONNESSIONE CON IL SERVER */
                while(!clientSocket.isConnected()){
                    try{
                        clientSocket.connect();
                    }catch(IOException e){
                        System.out.println("Tentativo di connessione fallito, riprovo tra: "+RECONNECTION_DELAY+" ms.");
                        clientSocket.waitForReconnect(RECONNECTION_DELAY);
                    }
                }

                /* UTENTE NON ANCORA LOGGATO */
                loggedIn = false;
                try{
                    /* MENU UTENTE */
                    startInterfaceClient();
                }finally{
                    loggedIn = false;
                    clientSocket.disconnect();
                    listenerSocket.close();
                    stopNotificationListener();
                }

            } catch (Exception e) {
               //CATTURO OGNI ALTRA ECCEZZIONE E RIPARTO
                System.out.println("Errore imprevisto: "+e.getMessage()+". Riparto tra: "+RECONNECTION_DELAY+" ms.");
                try {
                    Thread.sleep(RECONNECTION_DELAY);
                }catch(InterruptedException ie){};
            }
        }
        System.out.println("Disconnessione dal server in corso...");
        clientSocket.disconnect();
        listenerSocket.close();
        stopNotificationListener();
        System.out.println("Disconnessione dal server riuscita.");
    }

    private static void startInterfaceClient() throws IOException{
        int operation = 0;
        userScan = new Scanner(System.in);

        while(isRunning){
            displayMenu(); //MENU TESTUALE OPERAZIONI
            try {

                //LETTURA INPUT UTENTE
                String line = userScan.nextLine();

                //IGNORO SE INPUT VUOTO
                if (line.trim().isEmpty()) {
                    continue;
                }

                //CAST IN INTERO
                operation = Integer.parseInt(line);
            } catch (NumberFormatException err) {
                System.err.println("Errore: Operazione non valida.");
                continue;
            }
            if (operation == 0) {
                isRunning = false;
                return; //esco dal loop e torno al chiamante
            }

            try{
                switch(operation){
                    case 1:
                        if(!loggedIn) handleRegister();
                        else System.out.println("-- Fare logout e riprovare.");
                        break;
                    case 2: handleUpdateCredential();
                        break;
                    case 3:
                        if(!loggedIn) handleLogin();
                        else System.out.println("-- Login effettuato.");
                        break;
                    case 4:
                        if(loggedIn) {
                            handleLogout(); return; //esco dal loop e torno al chiamante
                        } else System.out.println(" -- Login non effettuato");
                        break;
                    case 5:
                        if(loggedIn) handleLimitOrder();
                        else System.out.println(" -- Login non effettuato.");
                        break;
                    case 6:
                        if(loggedIn) handleMarketOrder();
                        else System.out.println("-- Login non effettuato.");
                        break;
                    case 7:
                        if(loggedIn) handleStopOrder();
                        else System.out.println("-- Login non effettuato.");
                        break;
                    case 8:
                        if(loggedIn) handleCancelOrder();
                        else System.out.println("-- Login non effettuato.");
                        break;
                    case 9:
                        if(loggedIn) handlePriceHistory();
                        else System.out.println("-- Login non effettuato.");
                        break;
                    default:
                        System.err.println("Errore: Operazione non riconosciuta");
                }
            }catch(IOException e){
                System.err.println("Errore durante la comunicazione col server: "+e.getMessage());
                return;
            }
        }
    }

    private static void displayMenu(){
        System.out.println();
        System.out.println("---- CLIENT CROSS ----");
        System.out.println("[1] Register;");
        System.out.println("[2] Update Credentials;");
        System.out.println("[3] Login;");
        System.out.println("[4] Logout");
        System.out.println("[5] Insert Limit Order;");
        System.out.println("[6] Insert Market Order;");
        System.out.println("[7] Insert Stop Order;");
        System.out.println("[8] Cancel Order;");
        System.out.println("[9] Get Price History;");
        System.out.println("[0] Exit;");
        System.out.print("ID OPERAZIONE: ");
    }

    private static void handleRegister() throws IOException {
        /* INPUT UTENTE */
        System.out.print("Username: ");
        String username = userScan.nextLine();
        System.out.print("Passowrd: ");
        String password = userScan.nextLine();

        /* CLIENT TO SERVER */
        JsonObject userValues = new JsonObject();
        userValues.addProperty("username", username);
        userValues.addProperty("password", password);

        JsonObject opObj = new JsonObject();
        opObj.addProperty("operation", "register");
        opObj.add("values", userValues);

        clientSocket.sendRequest(opObj);

        /* SERVER TO CLIENT */
        JsonObject jo = clientSocket.receiveResponse();
        printResponseMessage(jo);
    }

    private static void handleUpdateCredential() throws IOException {
        /* INPUT UTENTE */
        System.out.print("Username: ");
        String username = userScan.nextLine();
        System.out.print("Old Passowrd: ");
        String password_old = userScan.nextLine();
        System.out.print("New Passowrd: ");
        String password_new = userScan.nextLine();

        /* CLIENT TO SERVER */
        JsonObject userValues = new JsonObject();
        userValues.addProperty("username", username);
        userValues.addProperty("password_old", password_old);
        userValues.addProperty("password_new", password_new);

        JsonObject opObj = new JsonObject();
        opObj.addProperty("operation", "updateCredentials");
        opObj.add("values", userValues);

        clientSocket.sendRequest(opObj);

        /* SERVER TO CLIENT */
        JsonObject jo = clientSocket.receiveResponse();
        printResponseMessage(jo);
    }

    private static void handleLogin() throws IOException {
        /* INPUT UTENTE */
        System.out.print("Username: ");
        String username = userScan.nextLine();
        System.out.print("Passowrd: ");
        String password = userScan.nextLine();

        /* CLIENT TO SERVER */
        JsonObject userValues = new JsonObject();
        userValues.addProperty("username", username);
        userValues.addProperty("password", password);
        userValues.addProperty("udpPort", UDP_PORT);

        JsonObject opObj = new JsonObject();
        opObj.addProperty("operation", "login");
        opObj.add("values", userValues);

        clientSocket.sendRequest(opObj);

        /* SERVER TO CLIENT */
        JsonObject jo = clientSocket.receiveResponse();
        printResponseMessage(jo);
        if(jo.get("response").getAsInt() == 100){
            loggedIn = true;
        }
    }

    private static void handleLogout() throws IOException {
        JsonObject opObj = new JsonObject();
        opObj.addProperty("operation", "logout");
        opObj.add("values", new JsonObject());

        clientSocket.sendRequest(opObj);

        /* SERVER TO CLIENT */
        JsonObject jo = clientSocket.receiveResponse();
        printResponseMessage(jo);
        loggedIn = false;
    }

    private static void handleLimitOrder() throws IOException {
        /* INPUT UTENTE */
        System.out.print("type [ask/bid]: ");
        String type = userScan.nextLine();
        System.out.print("size: ");
        int size = Integer.parseInt(userScan.nextLine());
        System.out.print("price: ");
        int price = Integer.parseInt(userScan.nextLine());

        /* CLIENT TO SERVER */
        JsonObject userValues = new JsonObject();
        userValues.addProperty("type", type);
        userValues.addProperty("size", size);
        userValues.addProperty("price", price);

        JsonObject opObj = new JsonObject();
        opObj.addProperty("operation", "insertLimitOrder");
        opObj.add("values", userValues);

        clientSocket.sendRequest(opObj);

        /* SERVER TO CLIENT */
        JsonObject jo = clientSocket.receiveResponse();
        printResponseOrder(jo);
    }

    private static void handleMarketOrder() throws IOException {
        /* INPUT UTENTE */
        System.out.print("type [ask/bid]: ");
        String type = userScan.nextLine();
        System.out.print("size: ");
        int size = Integer.parseInt(userScan.nextLine());

        /* CLIENT TO SERVER */
        JsonObject userValues = new JsonObject();
        userValues.addProperty("type", type);
        userValues.addProperty("size", size);

        JsonObject opObj = new JsonObject();
        opObj.addProperty("operation", "insertMarketOrder");
        opObj.add("values", userValues);

        clientSocket.sendRequest(opObj);

        /* SERVER TO CLIENT */
        JsonObject jo = clientSocket.receiveResponse();
        printResponseOrder(jo);
    }

    private static void handleStopOrder() throws IOException {
        /* INPUT UTENTE */
        System.out.print("type [ask/bid]: ");
        String type = userScan.nextLine();
        System.out.print("size: ");
        int size = Integer.parseInt(userScan.nextLine());
        System.out.print("stop-price: ");
        int price = Integer.parseInt(userScan.nextLine());

        /* CLIENT TO SERVER */
        JsonObject userValues = new JsonObject();
        userValues.addProperty("type", type);
        userValues.addProperty("size", size);
        userValues.addProperty("price", price);

        JsonObject opObj = new JsonObject();
        opObj.addProperty("operation", "insertStopOrder");
        opObj.add("values", userValues);

        clientSocket.sendRequest(opObj);

        /* SERVER TO CLIENT */
        JsonObject jo = clientSocket.receiveResponse();
        printResponseOrder(jo);
    }

    private static void handleCancelOrder() throws IOException {
        /* INPUT UTENTE*/
        System.out.print("Order id: ");
        int orderId = Integer.parseInt(userScan.nextLine());

        /* CLIENT TO SERVER */
        JsonObject userValues = new JsonObject();
        userValues.addProperty("orderId", orderId);

        JsonObject opObj = new JsonObject();
        opObj.addProperty("operation", "cancelOrder");
        opObj.add("values", userValues);

        clientSocket.sendRequest(opObj);

        /* SERVER TO CIENT */
        JsonObject jo = clientSocket.receiveResponse();
        printResponseMessage(jo);
    }

    private static void handlePriceHistory() throws IOException {
        /* INPUT UTENTE */
        System.out.print("Month (MMYYYY) :  ");
        String month = userScan.nextLine();

        /* CLIENT TO SERVER */
        JsonObject userValues = new JsonObject();
        userValues.addProperty("month", month);

        JsonObject opObj = new JsonObject();
        opObj.addProperty("operation", "getPriceHistory");
        opObj.add("values", userValues);

        clientSocket.sendRequest(opObj);

        /* SERVER TO CLIENT */
        JsonObject response = clientSocket.receiveResponse();

        // COSTRUZIONE RISPOSTA PER L'UTENTE
        if (response.has("priceHistory")) {
            JsonArray historyArray = response.getAsJsonArray("priceHistory");
            System.out.println("Storico prezzi per il mese richiesto:");

            for (JsonElement element : historyArray) {
                /* OGGETTO JSON CHE RAPPRESENTA UN SINGOLO GIORNO */
                JsonObject dayRecord = element.getAsJsonObject();

                /* RECUPERO INFORMAZIONI DI OGNI GIORNO */
                String date = dayRecord.get("date").getAsString();
                int openPrice = dayRecord.get("open").getAsInt();
                int closePrice = dayRecord.get("close").getAsInt();
                int maxPrice = dayRecord.get("max").getAsInt();
                int minPrice = dayRecord.get("min").getAsInt();

                System.out.println("Data: " + date +
                        " | Open Price: " + openPrice +
                        " | Close Price: " + closePrice +
                        " | Max Price: " + maxPrice +
                        " | Min Price: " + minPrice);
            }
        } else if (response.has("errorMessage")) {
            System.out.println("Errore: " + response.get("errorMessage").getAsString());
        } else {
            System.out.println("Risposta non valida ricevuta dal server.");
        }
    }

    /* HANDLER PER {"response": int, "errorMessage": String } */
    private static void printResponseMessage(JsonObject jo){
        int code = jo.get("response").getAsInt();
        String message = jo.get("errorMessage").getAsString();
        System.out.println("------------------------------");
        System.out.println("Risposta dal server: " + code + "-" + message);
        System.out.println("------------------------------");
    }

    /* HANDLER PER {"orderId": int } */
    private static void printResponseOrder(JsonObject jo){
        int orderId =  jo.get("orderId").getAsInt();
        if(orderId != -1){
            System.out.println("------------------------------");
            System.out.println("Ordine effettuato con successo [ID = " + orderId + "]");
            System.out.println("------------------------------");
        } else {
            System.out.println("------------------------------");
            System.out.println("Ordine fallito");
            System.out.println("------------------------------");
        }
    }

    private static void startNotificationListener() {
        if (listenerThread == null || !listenerThread.isAlive()) {
            listenerThread = new Thread(listenerSocket);
            listenerThread.start();
        }
    }

    private static void stopNotificationListener() {
        if (listenerSocket != null) {
            listenerSocket.close();
        }
        if (listenerThread != null) {
            try {
                listenerThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void import_config(){
        config = new LoaderConfig("ConfClient.properties");
        SERVER_ADDRESS  = config.getStringProperty("SERVER_ADDRESS");
        SERVER_PORT = config.getIntProperty("SERVER_PORT");
        UDP_PORT = config.getIntProperty("UDP_PORT");
        RECONNECTION_DELAY = config.getIntProperty("RECONNECTION_DELAY_MILLISECONDS");
    }

}
