# vesys17
Repository der Gruppe Kevin Kirn und Hoang Tran (FS17).

## Übung1
Beschreibung der Lösung.

### Client-Tier
Wird ein Bank-Client gestartet (bank.sockets.Driver), so wird die connect() Methode aufgerufen. In dieser Methode wird ein erster Socket zum Server aufgebaut, dies dient lediglich dazu zu testen ob er Server erreichbar ist. Aktionen, welche auf dem Client ausgeführt werden können (z.B. Account anlegen, Deposit, etc.), werden grundsätzlich immer zuerst auf dem Server ausgeführt. Hierzu werden ObjectStreams verwendet, welche Instanzen von entsprechenden Kommando-Klassen an den Server senden. Als Response des Servers wird jeweils eine Instanz des verwendeten Kommandos erwartet, welche die gwünschten Daten oder Flags enthalten die Erfolg oder Misserfolg signalisieren. Der Client führt lokal eine Liste von Konten, diese dienen jedoch nur als Dummy-Instanzen von denjenigen auf dem Server. Wird also z.B. eine Deposit-Operation auf einem lokalen Konto ausgeführt, so folgt dies dem selben Grundsatz, dass dies zuerst auf dem Server ausgeführt wird und lokale Dummy-Instanzen lediglich aktualisiert werden. Methoden in welchen Exceptions erwartet werden, erhalten vom Server die geworfene Exception so dass diese lokal ausgelöst werden kann.

### Server-Tier
Der Bank-Server verwaltet defacto eine lokale Bank, welche mit Aufgabe a) implementiert wurde. Alle Requests werden auf dieser Bank ausgeführt, Exceptions abgefangen und an die Clients gesendet. Wie bereits im Client-Tier erwähnt, treffen als Requests Instanzen von Kommandos ein. Die Methode handleRequest() ordnet jeden Request einem solchen Kommando zu und führt die entsprechenden Operationen auf der lokalen Bank aus. In der Instanz des vom Client gesendeten Kommandos werden die Resultate danach hinterlegt (Erfolg, Werte oder Fehlermeldungen) und wieder als Response an den Client gesendet. Danach ist die Anfrage abgearbeitet weshalb der Server den bestehnden Socket zum Client schliesst.

### Performance
Aktuell wird für jede Operation jeweils ein neuer Socket vom Client geöffnet und nach Abarbeitung durch den Server geschlossen. Es gibt hier sicherlich noch Verbesserungspotential wo sich eigentlich nachfolgende Request bündeln liessen um den Overhead und damit die Performance zu verbessern. Ebenso werden vom Client teilweise alle Accounts direkt vom Server abgefragt, um zu verhindern, dass die lokalen Daten veraltet sind. Dies könnte dahingehend optimiert werden, dass nur jene synchronisiert werden bei welchen sich Zustände verändert haben.
