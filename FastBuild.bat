@echo off
echo ------------------------------------
echo Compilazione dei file .java...
echo ------------------------------------

REM Compila lato CLIENT
javac -d out/client -cp lib/gson-2.10.1.jar src/client/*.java src/util/*.java

REM Compila lato SERVER
javac -d out/server -cp lib/gson-2.10.1.jar src/server/*.java src/util/*.java

echo ------------------------------------
echo Creazione dei file .jar...
echo ------------------------------------

REM Crea il jar del CLIENT
jar cfm Client.jar ManifestClient.txt -C out/client . 

REM Crea il jar del SERVER
jar cfm Server.jar ManifestServer.txt -C out/server .

echo ------------------------------------
echo Build completata con successo!
echo ------------------------------------

pause