import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UDPserver {
    /* UDP DATA */
    private DatagramSocket udpSocket;

    /* USER DATA */
    private final InetAddress clientAddress;
    private final int clientPort;

    public UDPserver(InetAddress clientAddress, int clientPort) {
        this.clientAddress = clientAddress;
        this.clientPort = clientPort;
    }

    /* SCRIPT PER CREARE UN PACCHETTO UDP E INVIARLO ALL'UTENTE*/
    public void send(String message) {
        try {
            udpSocket = new DatagramSocket();
            byte[] data = message.getBytes();

            DatagramPacket packet = new DatagramPacket(
                    data, data.length, clientAddress, clientPort
            );

            udpSocket.send(packet);
            System.out.println("Notifica Inviata al client [ " + clientAddress + ":" + clientPort+"].");

            /* ATTESA PRIMA DI CHIUDERE LA SOCKET (configurabile)*/
            Thread.sleep(MainServer.UDP_TIMEOUT);

        } catch (IOException e) {
            System.out.println("Errore Invio Notifica al client [ " + clientAddress + ":" + clientPort+"].");
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        this.close();
    }

    public void close(){
        if(udpSocket != null && !udpSocket.isClosed())
            udpSocket.close();
    }
}
