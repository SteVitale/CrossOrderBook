# CrossOrderBook
# Progetto Laboratorio III - Order Book di Criptovalute

## Descrizione
Questo progetto implementa un **order book**, servizio fondamentale nei mercati finanziari centralizzati, focalizzato sugli exchange di criptovalute (ad esempio Binance, Coinbase, Kraken). L'order book è un registro di tutti gli ordini di acquisto (bid) e vendita (ask) per la coppia **BTC/USD**, con prezzi e volumi, utilizzato per valutare domanda e offerta e permettere scambi equi ed efficienti.

## Struttura della cartella
```
StefanoVitaleLab3/
├── lib/
│   └── gson-2.10.1.jar       # Libreria Gson
├── out/
│   ├── client/              # .class lato client
│   └── server/              # .class lato server
├── src/
│   ├── client/              # Classi client
│   ├── server/              # Classi server
│   └── util/                # Classi condivise
├── ConfClient.properties    # Esempio configurazione client
├── ConfServer.properties    # Esempio configurazione server
├── FastRun.bat              # Script di build
├── ManifestClient.txt       # Manifest per jar client
├── ManifestServer.txt       # Manifest per jar server
└── RelazioneProgetto.pdf    # Documentazione
```

### Esempio: cartella Client_1
```
Client_1/
├── lib/gson-2.10.1.jar
├── Client.jar
└── ConfClient.properties    # Configurazione specifica per Client_1
```

### Esempio: cartella Server
```
Server/
├── lib/gson-2.10.1.jar
├── Server.jar
└── ConfServer.properties    # Configurazione server
```

## Tecnologie e dipendenze
- **Java 8**
- **Gson 2.10.1** per serializzazione/deserializzazione JSON
- **Protocollo TCP/UDP** per comunicazione client-server e notifiche asincrone

## Scelte implementative
1. **Messaggi JSON**: serializzazione con `gson.toJson()`, deserializzazione con `gson.fromJson()`.
2. **getPriceHistory**: raggruppa transazioni per data usando `TreeMap` e converte timestamp in GMT con `Instant` e `ZonedDateTime`.
3. **Notifiche UDP**: il client comunica al server la porta UDP per ricevere aggiornamenti asincroni.
4. **Threading**: thread pool per gestire connessioni TCP (lato server) e thread dedicato UDP (lato client).
5. **Strutture dati**:
   - `ConcurrentHashMap<Integer, Order>` per il book degli ordini
   - `PriorityQueue<LimitOrder>` per book BID e ASK con priorità price/time
   - `ArrayList<StopOrder>` per stop order in attesa
   - `ConcurrentHashMap` per gestione utenti, porte UDP e indirizzi IP
6. **Sincronizzazione**:
   - Blocco `synchronized` per operazioni critiche sull'OrderBook
   - Lock per l'accesso concorrente al file JSON delle transazioni

## Configurazione
### ConfServer.properties
```properties
PORT=12345
CORE_POOL_SIZE=4
MAX_POOL_SIZE=10
EXECUTOR_INACTIVITY_TIME_MILLISECONDS=350000
CLIENT_TIMEOUT_MILLISECONDS=300000
EXECUTOR_TIMEOUT_MILLISECONDS=400000
TRANSACTION_FILE=transactions.json
UDP_TIMEOUT_CLOSE_MILLISECONDS=1000
DIM_QUEUE_TASK=50
```

### ConfClient.properties
```properties
SERVER_ADDRESS=localhost
SERVER_PORT=12345
UDP_PORT=54321
RECONNECTION_DELAY_MILLISECONDS=5000
```

## Build e Creazione JAR
Eseguire dalla cartella principale del progetto:

1. **Compilazione**
   - Lato server:
     ```bash
     javac -d out/server -cp lib/gson-2.10.1.jar src/server/*.java src/util/*.java
     ```
   - Lato client:
     ```bash
     javac -d out/client -cp lib/gson-2.10.1.jar src/client/*.java src/util/*.java
     ```

2. **Creazione JAR**
   - Lato server:
     ```bash
     jar cfm Server.jar ManifestServer.txt -C out/server .
     ```
   - Lato client:
     ```bash
     jar cfm Client.jar ManifestClient.txt -C out/client .
     ```

## Esecuzione
- **Server**:
  ```bash
  java -jar Server.jar
  ```
- **Client**:
  ```bash
  java -jar Client.jar
  ```

## Uso
Al client viene mostrato un menù numerato di operazioni. Premere il numero corrispondente per selezionare:
- `[0]` Chiude il client in modo ordinato.
- Altri numeri per invoke getPrice, submit Order, ecc.

In caso di disconnessione, il client tenterà automaticamente la riconnessione dopo il ritardo configurato.

---
*Progetto Laboratorio III di Stefano Vitale*

