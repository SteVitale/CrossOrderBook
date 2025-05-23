import com.google.gson.*;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

public class ServerExecutor implements Runnable{
    /* CONNECTION DATA */
    private Socket currentClient;
    private PrintWriter write;
    private BufferedReader read;

    /* SHARED DATA */
    private final UserManager userManager;
    private final OrderBook orderBook;

    /* LOGGED USER DATA */
    private User loggedUser ;
    private ResponseMessage responseMessage;

    /* JSON DATA */
    private Gson gson;

    public ServerExecutor(UserManager um, OrderBook ob, Socket s){
        this.userManager = um;
        this.orderBook = ob;
        this.currentClient = s;
        this.gson = new Gson();
    }

    @Override
    public void run(){
        System.out.println("Connessione Effettuata: " + currentClient.getInetAddress() + ":"+currentClient.getPort());
        try{
            this.currentClient.setSoTimeout((int)(MainServer.TIMEOUT_DISCONNECTION_USER));
            InputStream input = currentClient.getInputStream();
            OutputStream output = currentClient.getOutputStream();

            write = new PrintWriter(output, true); //SCRIVE NELLA CONNESSIONE
            read = new BufferedReader(new InputStreamReader(input)); //LEGGE DALLA CONNESSIONE

            String line;
            while((line = read.readLine()) != null){
                //DESERIALIZZAZIONE
                JsonObject jo = gson.fromJson(line, JsonObject.class);

                //OPERAZIONE RICEVUTA
                String operation = jo.get("operation").getAsString();

                //DATI RELATIVI ALL'OPERAZIONE
                JsonObject values = jo.getAsJsonObject("values");

                switch(operation){
                    case "register":
                        handleRegister(values);
                        break;
                    case "updateCredentials":
                        handleUpdateCredentials(values);
                        break;
                    case "login":
                        handleLogin(values);
                        break;
                    case "logout":
                        handleLogout();
                        break;
                    case "insertLimitOrder":
                        handleInsertLimitOrder(values);
                        break;
                    case "insertMarketOrder":
                        handleInsertMarketOrder(values);
                        break;
                    case "insertStopOrder":
                        handleInsertStopOrder(values);
                        break;
                    case "cancelOrder":
                        handleCancelOrder(values);
                        break;
                    case "getPriceHistory":
                        handleGetPriceHistory(values);
                        break;
                }
            }
        } catch(SocketTimeoutException ste) {
            System.out.println("Disconnessione utente inattivo ["+ currentClient.getInetAddress() + ":"+currentClient.getPort()+"].");
        } catch (IOException | NoSuchElementException err) {
            System.err.println("Errore operazione non riconosciuta: " + err.getMessage());
        } finally {
            if (loggedUser != null) {
                userManager.removeLoggedUsers(loggedUser.getUsername());
                loggedUser = null;
            }
            cleanConnection();
        }
    }

    private void handleRegister(JsonObject values) {
        try {
            /* ESTRAZIONE VALORI RICHIESTA */
            String username = values.get("username").getAsString();
            String password = values.get("password").getAsString();

            /* CREAZIONE RISPOSTA */
            responseMessage = userManager.addUser(new User(username, password));
        } catch (Exception e) {
            responseMessage = new ResponseMessage(103, "other error cases");
        }
        /* INVIO RISPOSTA */
        write.println(gson.toJson(responseMessage));
    }

    private void handleUpdateCredentials(JsonObject values) {
        try {
            /* ESTRAZIONE VALORI RICHIESTA */
            String username = values.get("username").getAsString();
            String old_password = values.get("password_old").getAsString();
            String new_password = values.get("password_new").getAsString();
            User u = new User(username, old_password);

            /* CREAZIONE RISPOSTA */
            if(userManager.isUserLoggedIn(username)){
                //l'utente è loggato in un altro client
                responseMessage = new ResponseMessage(104, "user currently logged in");
            }else if (loggedUser != null && u.getUsername().equals(loggedUser.getUsername())) {
                //l'utente è loggato in questo client
                responseMessage = new ResponseMessage(104, "user currently logged in");
            } else {
                //a livello di utenti loggati è possibile provare ad aggiornare i dati, il controllo passa al manager
                responseMessage = userManager.updateUser(new User(u.getUsername(), u.getPassword()), new_password);
            }
        } catch (Exception e) {
            responseMessage = new ResponseMessage(105, "other error cases");
        }

        /* INVIO RISPOSTA */
        write.println(gson.toJson(responseMessage));
    }

    private void handleLogin(JsonObject values) {
        try {
            /* ESTRAZIONE VALORI RICHIESTA */
            String username = values.get("username").getAsString();
            String password = values.get("password").getAsString();
            int udpPort = values.get("udpPort").getAsInt();

            User u = new User(username, password);

            /* CREAZIONE RISPOSTA */
            if (loggedUser != null && u.getUsername().equals(loggedUser.getUsername())) {
                responseMessage = new ResponseMessage(102, "user already logged in");
            } else {
                responseMessage = userManager.VerifyUser(u);
            }
            if (responseMessage.getErrorMessage().equals("OK") && loggedUser == null) {
                loggedUser = new User(username, password);
                userManager.updateLoggedUsers(username, currentClient.getInetAddress(), udpPort);
            }
        } catch (Exception e) {
            responseMessage = new ResponseMessage(103, "other error cases");
        }

        /* INVIO RISPOSTA */
        write.println(gson.toJson(responseMessage));
    }

    private void handleLogout() {
        /* CREAZIONE RISPOSTA */
        if (loggedUser != null && userManager.isUserLoggedIn(loggedUser.getUsername())) {
            userManager.removeLoggedUsers(loggedUser.getUsername());
            loggedUser = null;
            responseMessage = new ResponseMessage(100, "OK");
        } else {
            responseMessage = new ResponseMessage(101, "user not logged in or other error cases");
        }

        /* INVIO RISPOSTA */
        write.println(gson.toJson(responseMessage));
        cleanConnection();
    }

    private void handleInsertLimitOrder(JsonObject values){
        /* ESTRAZIONE VALORI RICHIESTA */
        int price = values.get("price").getAsInt();
        String type = values.get("type").getAsString();
        int size = values.get("size").getAsInt();

        /* GESTIONE ORDINE */
        Order.Type t = Order.StringToType(type);
        int id = -1;
        if(t != null){
            Order limitOrder = new LimitOrder(t, size, loggedUser, price);
            id = orderBook.addOrder(limitOrder);
        }

        /* CREAZIONE E INVIO RISPOSTA */
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("orderId", id);
        write.println(gson.toJson(jsonObject));
    }

    private void handleInsertMarketOrder(JsonObject values){
        /* ESTRAZIONE VALORI RICHIESTA */
        String type = values.get("type").getAsString();
        int size = values.get("size").getAsInt();

        /* GESTIONE ORDINE */
        Order.Type t = Order.StringToType(type);
        int id = -1;

        Order marketOrder;
        if(t != null){
            marketOrder = new MarketOrder(t, size, loggedUser);
            id = orderBook.addOrder(marketOrder);

        }

        /* CREAZIONE E INVIO RISPOSTA */
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("orderId", id);
        write.println(gson.toJson(jsonObject));
    }

    private void handleInsertStopOrder(JsonObject values){
        /* ESTRAZIONE VALORI RICHIESTA */
        int price = values.get("price").getAsInt();
        String type = values.get("type").getAsString();
        int size = values.get("size").getAsInt();

        /* GESTIONE ORDINE */
        int id = -1;
        Order.Type t = Order.StringToType(type);
        if(t != null){
            Order stopOrder = new StopOrder(t, size, loggedUser, price);
            id = orderBook.addOrder(stopOrder);
        }

        /* CREAZIONE E INVIO RISPOSTA */
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("orderId", id);
        write.println(gson.toJson(jsonObject));
    }

    private void handleCancelOrder(JsonObject values){
        /* ESTRAZIONE VALORI RICHIESTA */
        int id = values.get("orderId").getAsInt();

        /* GESTIONE ORDINE E CREAZIONE RISPOSTA*/
        if(orderBook.removeOrder(id, loggedUser.getUsername())){
            responseMessage = new ResponseMessage(100, "OK");
        } else {
            responseMessage = new ResponseMessage(101, "order does not exist or belongs to different user or has already been finalized or other error cases");
        }

        /* INVIO RISPOSTA */
        write.println(gson.toJson(responseMessage));
    }

    private void handleGetPriceHistory(JsonObject values){
        /* ESTRAZIONE VALORI RICHIESTA */
        String month = values.get("month").getAsString();
        int mm = Integer.parseInt(month.substring(0,2)); // indici 0 1
        int yyyy = Integer.parseInt(month.substring(2)); // indici 2 3 4 5

        String path = MainServer.TRANSACTIONS_FILE;

        //BLOCCO PER LEGGERE DAL FILE DELLE TRANSAZIONI EVASE
        synchronized (MainServer.transactionsFileLock){
            try{
                //RECUPERO JsonArray "trades" CHE CONTIENE TUTTI I RECORD
                FileReader read = new FileReader(path);
                JsonObject transactionObject = JsonParser.parseReader(read).getAsJsonObject();
                JsonArray transactionArray = transactionObject.getAsJsonArray("trades");

                //STRUTTURA DATI PER MEMORIZZARE OGNI GIORNO DEL MESE
                Map<String, DailyHistory> dailyMap = new TreeMap<>();

                //LEGGO OGNI RECORD DEL FILE
                for(JsonElement elem : transactionArray){
                    //RECUPERO TIMESTAMP E PREZZO DI UN RECORD
                    JsonObject transaction = elem.getAsJsonObject();
                    long timestamp = transaction.get("timestamp").getAsLong();
                    int price = transaction.get("price").getAsInt();

                    //CONVERSIONE TIMESTAMP IN GMT
                    Instant instant = Instant.ofEpochSecond(timestamp);
                    ZonedDateTime date = instant.atZone(ZoneOffset.UTC);
                    int year_transaction = date.getYear();
                    int month_transaction = date.getMonthValue();

                    //VERICO CHE FA PARTE DEI RECORD RICHIESTI DALL'UTENTE
                    if(yyyy == year_transaction && mm == month_transaction){
                        // formato "yyyy-MM-dd"
                        String dayString = date.toLocalDate().toString();

                        // aggiorno il giorno corrispondente
                        DailyHistory data = dailyMap.getOrDefault(dayString, new DailyHistory());
                        data.update(price);
                        dailyMap.put(dayString, data);
                    }
                }
                read.close();

                /* CREAZIONE E INVIO RISPOSTA */
                JsonObject response = new JsonObject();
                JsonArray priceHistory = new JsonArray();
                for (Map.Entry<String, DailyHistory> entry : dailyMap.entrySet()) {
                    JsonObject dayObj = new JsonObject();
                    dayObj.addProperty("date", entry.getKey());
                    dayObj.addProperty("open", entry.getValue().openPrice);
                    dayObj.addProperty("close", entry.getValue().closePrice);
                    dayObj.addProperty("max", entry.getValue().maxPrice);
                    dayObj.addProperty("min", entry.getValue().minPrice);
                    priceHistory.add(dayObj);
                }
                response.add("priceHistory", priceHistory);
                write.println(gson.toJson(response));

            }catch(IOException e){
                System.err.println("Errore nella lettura del file transazioni.");
                responseMessage = new ResponseMessage(101, "Errore recupero dati dello storico");
                write.println(gson.toJson(responseMessage));
            }
        }
    }

    private void cleanConnection(){
        try{
            if(currentClient != null && !currentClient.isClosed())
                currentClient.close();
        } catch(IOException e){
            System.err.println("Errore nella chiusura del socket...");
        }
    }


}