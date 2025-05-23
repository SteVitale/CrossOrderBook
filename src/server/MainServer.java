import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MainServer {

    /* CONFIG DATA */
    private static LoaderConfig serverConfig = null;
    public static int CORE_POOL_SIZE;
    public static int MAX_POOL_SIZE;
    public static long INACTIVITY_TIME;
    public static long TIMEOUT_DISCONNECTION_USER;
    public static long TIMEOUT_TERMINATION_EXECUTOR;
    public static long UDP_TIMEOUT;
    public static int PORT;
    public static int DIM_QUEUE_TASK;

    /* TRANSACTION FILE DATA */
    public static String TRANSACTIONS_FILE;
    public static final Object transactionsFileLock = new Object();

    public static void main(String[] args) throws IOException {
        // IMPORTA A RUNTIME I PARAMETRI NEL FILE CONFIGURAZIONE
        import_config();

        // CREA (SE NECESSARIO) IL FILE "transactions.json"
        createTransactionFile();

        // CREA L'OGGETTO UserManager CHE VERRA' PASSATO A TUTTI GLI EXECUTOR
        UserManager userManager = new UserManager();

        // CREA L'OGGETTO OrderBook CHE VERRA' PASSATO A TUTTI GLI EXECUTOR
        OrderBook orderBook = new OrderBook(userManager);

        //  CREAZIONE DEL POOL DI THREAD A CUI VERRA' ASSEGNATO UN CLIENT SU RICHIESTA
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                CORE_POOL_SIZE,  // Core pool size
                MAX_POOL_SIZE, // Maximum pool size
                INACTIVITY_TIME, // Tempo massimo di inattività dei thread extra
                TimeUnit.MILLISECONDS, //Unità in millisecondi
                new LinkedBlockingQueue<>(DIM_QUEUE_TASK), //Accodo task fino a DIM
                new ThreadPoolExecutor.AbortPolicy()
        );

        // IL MAIN SERVER SI METTE IN ASCOLTO
        try {
            ServerSocket listener = new ServerSocket(PORT);

            while (!pool.isShutdown()) {
                try {
                    //OGNI NUOVO CLIENT SARA' ASSEGNATO AD UN THREAD DEL POOL
                    Socket newClient = listener.accept();
                    pool.execute(new ServerExecutor(userManager, orderBook, newClient));
                } catch (RejectedExecutionException e) {
                    System.err.println("Task rifiutato a causa di overload: " + e.getMessage());
                }
            }

        }catch(IOException e){
            System.out.println("Errore durante l'esecuzione del server: " + e.getMessage());
        }finally {
            shutdownThreadExecutor(pool);
        }
    }

    private static void shutdownThreadExecutor(ThreadPoolExecutor pool){
        pool.shutdown(); // Avvia la chiusura ordinata
        try {
            if (!pool.awaitTermination(TIMEOUT_TERMINATION_EXECUTOR, TimeUnit.MILLISECONDS)) {
                pool.shutdownNow(); // Forza la chiusura
            }
        } catch (InterruptedException e) {
            pool.shutdownNow(); // Forza la chiusura in caso di interruzione
            Thread.currentThread().interrupt(); // Ripristina lo stato di interruzione
        }
    }

    private static void createTransactionFile(){
        //CREAZIONE FILE STORICO NEL PATH IMPORTATO DAL FILE DI CONFIGURAZIONE
        File file = new File(TRANSACTIONS_FILE);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        if(!file.exists()){
            //INSERISCO DI DEFAULT UN JsonArray VUOTO CHE CONTERRA' TUTTI I RECORD DI TRANSAZIONI EVASE
            //JsonObject obj = new JsonObject();
            //obj.add("trades", new JsonArray());

            try{
                FileWriter writer = new FileWriter(file);
                //writer.write(gson.toJson(obj));
                writer.close();
                System.out.println("Creazione file storico ["+file.getAbsolutePath()+"].");
            }catch (IOException e){
                System.err.println("Errore creazione file storico: " + e.getMessage());
            }
        }else {
            System.out.println("Rilevato file storico esistente ["+file.getAbsolutePath()+"].");
        }
    }

    private static void import_config(){
        //CHIAMATA A LoaderConfig PASSANDO MainServer.class
        serverConfig = new LoaderConfig("ConfServer.properties");

        //RECUPERO DEI PARAMETRI PER CONFIGURAZIONE SERVER
        PORT = serverConfig.getIntProperty("PORT");
        CORE_POOL_SIZE = serverConfig.getIntProperty("CORE_POOL_SIZE");
        MAX_POOL_SIZE = serverConfig.getIntProperty("MAX_POOL_SIZE");
        INACTIVITY_TIME = serverConfig.getLongProperty("EXECUTOR_INACTIVITY_TIME_MILLISECONDS");
        TIMEOUT_DISCONNECTION_USER = serverConfig.getLongProperty("CLIENT_TIMEOUT_MILLISECONDS");
        TIMEOUT_TERMINATION_EXECUTOR = serverConfig.getLongProperty("EXECUTOR_TIMEOUT_MILLISECONDS");
        UDP_TIMEOUT = serverConfig.getLongProperty("UDP_TIMEOUT_CLOSE_MILLISECONDS");
        DIM_QUEUE_TASK = serverConfig.getIntProperty("DIM_QUEUE_TASK");

        /*SE IL PATH DEL FILE DELLE TRANSAZIONI NON E' ASSOLUTO
        ASSICURO CHE VENGA INSERITO NELLA WORKDIR */
        String tempPath = serverConfig.getStringProperty("TRANSACTION_FILE");
        File file = new File(tempPath);

        if(!file.isAbsolute()){
            String dir = System.getProperty("user.dir");
            tempPath = dir + File.separator + tempPath;
        }

        TRANSACTIONS_FILE = tempPath;

        //deve valore timeout_utente < timeout_inattività < timeout_executor
        if(!(TIMEOUT_DISCONNECTION_USER < INACTIVITY_TIME
                && INACTIVITY_TIME < TIMEOUT_TERMINATION_EXECUTOR)){
            System.out.println("Valori timeout incoerenti: uso valori di default.");

            //valori di default per i timeout
            TIMEOUT_DISCONNECTION_USER = 300000; //300 secondi
            INACTIVITY_TIME = 350000; //350 secondi
            TIMEOUT_TERMINATION_EXECUTOR = 400000; //400 secondi
        }

    }
}
