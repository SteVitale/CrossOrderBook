import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;

public class UserManager {
    /* STRUTTURE PER DATI DELL'UTENTE */
    private ConcurrentHashMap<String, User> userMap = null; // USERNAME -> DATI PERSONALI

    /* STRUTTURE PER DATI DELLA CONNESSIONE DA/A UTENTE */
    private ConcurrentHashMap<String, Integer> userUdpPorts = null; // USERNAME -> PORTA UDP (ULTIMO LOGIN)
    private ConcurrentHashMap<String, InetAddress> userInetAddresses = null; // USERNAME -> INDIRIZZO IP (ULTIMO LOGIN)

    //INIZIALIZZAZIONE STRUTTURE DATI
    public UserManager(){
        userMap = new ConcurrentHashMap<>();
        userUdpPorts = new ConcurrentHashMap<>();
        userInetAddresses = new ConcurrentHashMap<>();
    }

    //GETTER E SETTER
    public ResponseMessage addUser(User u){

        if(u.getPassword().isEmpty())
            return new ResponseMessage(101, "invalid password");
        if(userMap.putIfAbsent(u.getUsername(), u) == null){
            return new ResponseMessage(100, "OK");
        }else return new ResponseMessage(102, "username not available");
    }

    public synchronized ResponseMessage updateUser(User u, String new_password){
        User x = getUserByUsername(u.getUsername());

        if(new_password.isEmpty())
            return new ResponseMessage(101, "invalid new password");

        else if ((x == null )|| (!x.getPassword().equals(u.getPassword()))) {

            return new ResponseMessage(102, "username/old_password mismatch or non existent username");
        } else if(x.getPassword().equals(new_password)){

            return new ResponseMessage(103, "new password, equal to old one");
        } else {
            x.setPassword(new_password);
            return new ResponseMessage(100, "OK");
        }
    }

    public synchronized boolean removeUser(User u){
        if(!isUserLoggedIn(u.getUsername()))//controllo prima se è loggato
            return userMap.remove(u.getUsername()) != null;
        return false;
    }

    public int getUserCount(){
        return userMap.size();
    }

    public User getUserByUsername(String username) {
        return userMap.get(username);
    }

    public synchronized ResponseMessage VerifyUser(User u){
        User x = getUserByUsername(u.getUsername());
        if ((x == null) || (!x.getPassword().equals(u.getPassword()))) {
            return new ResponseMessage(101, "username/password mismatch or non existent username");
        }  else {
            return new ResponseMessage(100, "OK");
        }
    }

    public synchronized int getUdpPort(String username){
        return userUdpPorts.getOrDefault(username, -1);
    }

    public InetAddress getUserInetAddress(String username) {
        return userInetAddresses.get(username);
    }

    /* GESTIONE UTENTI LOGGATI E SALVATAGGIO DEI DATI "UTILI" */
    public synchronized void updateLoggedUsers(String username, InetAddress ip, int udpPort){
        userInetAddresses.put(username, ip);
        userUdpPorts.put(username, udpPort);
    }

    public synchronized boolean isUserLoggedIn(String username){
        return userInetAddresses.containsKey(username) && userUdpPorts.containsKey(username);
    }

    public synchronized void removeLoggedUsers(String username){
        userInetAddresses.remove(username);
        userUdpPorts.remove(username);
    }
}
