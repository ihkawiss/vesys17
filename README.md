# vesys17
Repository der Gruppe Kevin Kirn und Hoang Tran (FS17).

## Übung1: Socket Bank
Beschreibung der Lösung.

### Client-Tier
Wird ein Bank-Client gestartet (bank.sockets.Driver), so wird die connect() Methode aufgerufen. In dieser Methode wird ein erster Socket zum Server aufgebaut, dies dient lediglich dazu zu testen ob der Server erreichbar ist. Aktionen, welche auf dem Client ausgeführt werden können (z.B. Account anlegen, Deposit, etc.), werden grundsätzlich immer zuerst auf dem Server ausgeführt. Hierzu werden ObjectStreams verwendet, welche Instanzen von entsprechenden Kommando-Klassen an den Server senden. Als Response des Servers wird jeweils eine Instanz des verwendeten Kommandos erwartet, welche die gwünschten Daten oder Flags enthalten die Erfolg oder Misserfolg signalisieren. Der Client führt lokal eine Liste von Konten, diese dienen jedoch nur als Dummy-Instanzen von denjenigen auf dem Server. Wird also z.B. eine Deposit-Operation auf einem lokalen Konto ausgeführt, so folgt dies dem selben Grundsatz, dass dies zuerst auf dem Server ausgeführt wird und lokale Dummy-Instanzen lediglich aktualisiert werden. Methoden in welchen Exceptions erwartet werden, erhalten vom Server die geworfene Exception so, dass diese lokal ausgelöst werden kann.

### Server-Tier
Der Bank-Server verwaltet defacto eine lokale Bank, welche mit Aufgabe a) implementiert wurde. Alle Requests werden auf dieser Bank ausgeführt, Exceptions abgefangen und an die Clients gesendet. Wie bereits im Client-Tier erwähnt, treffen als Requests Instanzen von Kommandos ein. Die Methode handleRequest() ordnet jeden Request einem solchen Kommando zu und führt die entsprechenden Operationen auf der lokalen Bank aus. In der Instanz des vom Client gesendeten Kommandos werden die Resultate danach hinterlegt (Erfolg, Werte oder Fehlermeldungen) und wieder als Response an den Client gesendet. Danach ist die Anfrage abgearbeitet weshalb der Server den bestehnden Socket zum Client schliesst.

### Performance
Aktuell wird für jede Operation jeweils ein neuer Socket vom Client geöffnet und nach Abarbeitung durch den Server geschlossen. Es gibt hier sicherlich noch Verbesserungspotential wo sich eigentlich nachfolgende Request bündeln liessen um den Overhead und damit die Performance zu verbessern. Ebenso werden vom Client teilweise alle Accounts direkt vom Server abgefragt, um zu verhindern, dass die lokalen Daten veraltet sind. Dies könnte dahingehend optimiert werden, dass nur jene synchronisiert werden bei welchen sich Zustände verändert haben.

## Übung2: HTTP Bank

### Client -Tier
Die Driver-Klasse (bank.http.Driver) basiert auf der Übung 1 und setzt somit auch serialisierte Kommando-Klassen zur Kommunikation ein. Im Gegesatz zur Übung 1 werden die Anfragen jedoch mittels HTTP übermittelt. Die Kommando-Instanzen werden serialisiert mittels POST-Request im Body an den Server übermittelt. In der vom Server gesendeten Response ist jeweils wieder das Kommando enthalten, dieses wird deserialisiert und analog Übung 1 weiter verarbeitet.

### Server-Tier
Wie schon der Client, basiert auch die HTTP Implementation des Servers auf der Übung 1. Der Server verarbeitet HTTP Anfragen der Clients, wertet die serialisierten Kommando-Objekte aus und führt entsprechende Anweisungen auf der Bank aus. Als Resultat sendet der Server den HTTP Status und die Kommando-Obekte zurück an die Clients.

## Übung6: JMS Bank
Die Driver-Klasse (bank.jms.Driver) basiert auf den vorhergehenden Übungen, es werden also bestehende Commands verwendet welche dann serialisiert mittels JMS übertragen bzw. gesendet werden. Der Driver implementiert jedoch das BankDriver2 Interface wordurch ein UpdateHandler zur Verfügung gestellt wird welcher bei Änderungen ausgeführt wird. Im Server Teil wurde ebenfalls die bereits bestehende Lösung so angepasst, dass die Kommunikation mittels JMS erfolgt. Der Server ist im Paket jmsServer zu finden und kann über die darin enthaltene Main Methode ausgeführt werden.

## Übung7: Websockets
In dieser Übung sollen Websockets als Transportmittel für die Commands eingesetzt werden. Es wird ein tyrus Server gestartet der einen entsprechenden ServerEndpoint zur Verfügung stellt auf welchem die Commands der Clients auf der ServerBank ausgeführt werden. Entsprechend ist der Client ebenso mittels tyrus implementiert worden, wobei der Driver eine WebSocket Verbindung zum ServerEndpoint aufbaut (auch hier gibt es einen tyrus ClientEndpoint). Leider ist es mir nicht gelungen diese Übung zu lösen, beim Versuch zwischen Client und Server eine Verbindung auf zu bauen schlägt die Anwendung fehl mit "Caused by: java.net.ConnectException: Connection refused: no further information" welches ich bis jetzt nicht lösen konnte. Der Server scheint jedoch zu funktionieren.
