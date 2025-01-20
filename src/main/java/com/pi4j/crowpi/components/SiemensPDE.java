package com.pi4j.crowpi.components;

import com.fazecast.jSerialComm.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.ini4j.Ini;
import com.pi4j.Pi4J;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import static java.lang.System.exit;
import static java.lang.Thread.sleep;



public class SiemensPDE {
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray(); //lookup
    Logger logger = LoggerFactory.getLogger(SiemensPDE.class);
    //Serial sreial_B;
    SerialPort serial_B;
    Garage garage = readInit();
    String serialBuffer = "";

    //******************************************************************************************************
    //*****************                          constructor SiemensPDE                    *****************
    //******************************************************************************************************

    // handle Siemens PDE telegram on USB serial port -> serial_B
    public SiemensPDE() {
        var pi4j = Pi4J.newAutoContext();
        serial_B = SerialPort.getCommPort("/dev/ttyUSB0");
        serial_B.setComPortParameters(9600, 8, 1, 0);

        logger.info("\n- - - System ready - - -\n");
        logger.info("waiting for serial telegrams.....");

        try {
            serial_B.openPort();
        } catch (Exception e) {
            logger.error("Can't open Serial port", e);
            exit(-1);
        }
        //*****************                  listen on Serial port B (extern USB0)         *****************
        try {
            logger.info("Is serial_B open?: " + serial_B.isOpen());
            logger.info("Is serial_B available?: " + serial_B.bytesAvailable());
            BufferedReader br = new BufferedReader(new InputStreamReader(serial_B.getInputStream()));
            String dataBuffer = "";
            boolean flag = false;

            while (serial_B.isOpen()) {
                var available = serial_B.bytesAvailable();
                if (available > 0) {
                    logger.debug("Available B: " + available);
                    for (int i = 0; i < available; i++) {
                        byte b = (byte) br.read();

                        if (b == 3 && !flag) { 				//here we receive the ETX char
                            flag = true;
                            dataBuffer += (char) b;
                        } else if (flag) {					//here we take the last byte for checksum
                            dataBuffer += (char) b;
                            handleSiemensPDE(dataBuffer);
                            dataBuffer = "";
                            flag = false;
                        } else {
                            dataBuffer += (char) b;                //here we are collecting the incoming message
                        }
                    }
                } else {
                    sleep(10);
                }
            }
        } catch (IOException | InterruptedException e) {
            logger.error("Exception occurred", e);
        }
    }
    //end of RStoIP

    //******************************************************************************************************
    //*****************                         convert byte to Hex                        *****************
    //******************************************************************************************************
    //convert bytes to HexString
    protected static String bytesToHex(byte[] input) {
        char[] hexChars = new char[input.length * 2];
        for (int j = 0; j < input.length; j++) {
            int v = input[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    //******************************************************************************************************
    //*****************                         import json settings                       *****************
    //******************************************************************************************************
    public Garage readInit() {

        Garage garage_tmp = new Garage();

        logger.info("\n\r- - - System start up - - -\n\r");
        logger.info("- - - EasyBox SiemensPDE rev.0.0.1 - - -");

        try {
            //read out config.ini file to get the jsonbin configuration
            File f = new File("/home/config.ini");
            Ini ini = new Ini(f);
            String bin_url = "";
            String bin_id = "";
            String bin_key = "";
            if (f.exists()) {
                bin_url = ini.get("SiemensPDE", "bin_url");
                bin_id = ini.get("SiemensPDE", "bin_id");
                bin_key = ini.get("SiemensPDE", "bin_key");
                logger.info("Readed information from config.ini: URL: "+bin_url + " bin_id: "+bin_id+ " key: "+bin_key);
            } else {
                logger.error("Invalid configuration.ini file. Exit program.");
                exit(-1);
            }

            //get JSON from jsonbin
            File jsonFile = new File("../"+bin_id+".json");     //should be /home/config.json
            JsonObject in = new JsonObject();


            // Check if the config json file exist, if not then get from remote and save it to local
            if (!(jsonFile.exists())) {

                // get JSON from JSON bin
                StringBuilder result = new StringBuilder();
                URL url = new URL(bin_url+bin_id);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("X-Access-Key", bin_key);
                conn.setRequestMethod("GET");
                try (
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(conn.getInputStream()))) {
                    for (String line; (line = reader.readLine()) != null; ) {
                        result.append(line);
                    }
                }


                // JSON String to JSON Object
                in = JsonParser.parseString(result.toString()).getAsJsonObject();

                // save JSON file to local
                jsonFile.createNewFile();
                try (PrintWriter out = new PrintWriter(new FileWriter(jsonFile.getPath()))) {
                    out.write(in.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }

            } else {

                // Parse the json file from local
                JsonElement jsonElement = null;
                try {
                    FileReader fileReader = new FileReader(jsonFile);
                    Reader reader = new InputStreamReader(new FileInputStream(jsonFile), StandardCharsets.UTF_8);
                    int ch = 0;
                    StringBuffer sb = new StringBuffer();
                    while((ch = reader.read()) != -1) {
                        sb.append((char) ch);
                    }
                    fileReader.close();
                    reader.close();
                    jsonElement = JsonParser.parseString((new FileReader(jsonFile.getPath())).toString());
                    jsonElement = JsonParser.parseString(sb.toString());
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                in = jsonElement.getAsJsonObject();
            }

            int count = 0;
            JsonObject in_config;
            in_config = in.get("record").getAsJsonObject().get("config").getAsJsonObject();

            count = in_config.get("[BASIC]").getAsJsonObject().size();    //count is number of ip tunnels
            for (int j = 0; j < count; j++) {

                garage_tmp.lineNumber = Integer.parseInt(in_config.get("[BASIC]").getAsJsonObject().get("[LINE-NO]").toString());
                garage_tmp.stationNumber = Integer.parseInt(in_config.get("[BASIC]").getAsJsonObject().get("[STATION-NO]").toString());
                garage_tmp.maxFreeSpacesShortTerm = Integer.parseInt(in_config.get("[BASIC]").getAsJsonObject().get("[MAX-ST]").toString());
                garage_tmp.maxFreeSpacesLongTerm = Integer.parseInt(in_config.get("[BASIC]").getAsJsonObject().get("[MAX-LT]").toString());
                garage_tmp.statusGarage = Integer.parseInt(in_config.get("[BASIC]").getAsJsonObject().get("[STATUS-PH]").toString());
                garage_tmp.statusSignage = Integer.parseInt(in_config.get("[BASIC]").getAsJsonObject().get("[STATUS-SIGNAGE]").toString());
                garage_tmp.statusManualControl = Integer.parseInt(in_config.get("[BASIC]").getAsJsonObject().get("[MAN-CTRL]").toString());
                garage_tmp.statusNightControl = Integer.parseInt(in_config.get("[BASIC]").getAsJsonObject().get("[NIGHT-CTRL]").toString());
                garage_tmp.numberOfDisplays = Integer.parseInt(in_config.get("[BASIC]").getAsJsonObject().get("[NUMBER-DISPLAYS]").toString());
                garage_tmp.command = Integer.parseInt(in_config.get("[BASIC]").getAsJsonObject().get("[CMD]").toString());
            }

            logger.info("LineNumber: "+garage_tmp.lineNumber+
                    "StationNumber: "+garage_tmp.stationNumber+
                    "FreeSpacesShortTerm: "+garage_tmp.freeSpacesShortTerm+
                    "FreeSpacesLongTerm: "+garage_tmp.freeSpacesLongTerm+
                    "StatusGarage: "+garage_tmp.statusGarage+
                    "StatusSignage: "+garage_tmp.statusSignage+
                    "StatusManualControl: "+garage_tmp.statusManualControl+
                    "StatusNightControl: "+garage_tmp.statusNightControl+
                    "NumberOfDisplays: "+garage_tmp.numberOfDisplays+
                    "Command: "+garage_tmp.command);



        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return garage_tmp;
    }


    //******************************************************************************************************
    //*****************                   handle incoming serial telegram                  *****************
    //******************************************************************************************************
    public void handleSiemensPDE(String input) throws IOException {
        logger.info("New Message from SIEMENS PDE: " + bytesToHex(input.getBytes()));

        // Konvertiere die Eingabe in ein Byte-Array und prüfe die Länge direkt
        byte[] data = input.getBytes();
        int telegramLength = data.length;
        logger.info("Length of telegram: " + telegramLength);

        // Überprüfen auf 4-Byte-Telegramm

        if (telegramLength == 4) {
            if (data[0] != 0x02 || data[2] != 0x03) {
                logger.warn("Invalid start or end byte for 4-byte telegram.");
                return;
            }

            byte calculatedCheckSum = (byte) (data[0] ^ data[1] ^ data[2]);
            byte receivedCheckSum = data[3];
            if (calculatedCheckSum != receivedCheckSum) {
                logger.warn("Checksum mismatch! Calculated: " + bytesToHex(new byte[]{calculatedCheckSum}) +
                        ", Received: " + bytesToHex(new byte[]{receivedCheckSum}));
                return;
            }

            logger.info("4-byte telegram with valid checksum.");
            processCommandFor4Bytes(data[1], data);						// Neue Methode zum Verarbeiten des 4-Byte-Kommandos
        } else if (telegramLength == 7 || telegramLength == 8) {
            logger.debug("Startcode: " + data[0]);

            byte[] lineNo = extractLineNumberSiemensPDE(data[1], data[2]);
            byte[] stationNo = extractStationNumberSiemensPDE(data[3], data[4]);
            byte command = data[5];
            logger.info("Received command byte: " + bytesToHex(new byte[]{command}));

            byte eot = data[telegramLength - 2];
            byte checkSum = data[telegramLength - 1];
            logger.debug("EOT Code: " + eot);

            byte calculatedCheckSum = 0;
            for (int i = 0; i < telegramLength - 1; i++) {
                calculatedCheckSum ^= data[i];
            }
            if (calculatedCheckSum != checkSum) {
                logger.warn("Checksum mismatch! Calculated: " + bytesToHex(new byte[]{calculatedCheckSum}) +
                        ", Received: " + bytesToHex(new byte[]{checkSum}));
                return;
            }

            logger.info("Checksum valid. Processing command...");
            processCommand(command, data);
        } else {
            logger.warn("Unsupported telegram length: " + telegramLength);
        }
    }

    //******************************************************************************************************
    //*****************                process command for 4-byte telegram                  *****************
    //******************************************************************************************************
    private void processCommandFor4Bytes(byte command, byte[] data) throws IOException {
        byte[] response;
        if (command == 0x01) {
            response = buildNewMessageFor4Bytes();
            logger.info("Response for 4-byte command created: " + bytesToHex(response));
        } else {
            logger.warn("Unsupported Command byte for 4-byte telegram: " + bytesToHex(new byte[]{command}) + ". Sending NAK (0x15)");
            response = new byte[]{0x15};
        }
        serial_B.writeBytes(response, response.length);
        logger.info("Response sent: " + bytesToHex(response));
    }

    //******************************************************************************************************
    //*****************                process command for 7- and 8-byte telegram            *****************
    //******************************************************************************************************
    private void processCommand(byte command, byte[] data) throws IOException {
        getValuesFromFile();
        byte[] response;
        byte[] lineNo = {data[1], data[2]};
        byte[] stationNo = {data[3], data[4]};

        if (command == 0x30) {
            response = buildMessageSiemens0x32(lineNo, stationNo);
            logger.info("Response for command 0x30 created: " + bytesToHex(response));
        } else if (command == 0x34) {
            response = buildMessageSiemens0x33(lineNo, stationNo);
            logger.info("Response for command 0x34 created: " + bytesToHex(response));
        } else {
            logger.warn("Unsupported Command byte: " + bytesToHex(new byte[]{command}) + ". Sending NAK (0x15)");
            response = new byte[]{0x15};
        }

        serial_B.writeBytes(response, response.length);
        logger.info("Response sent: " + bytesToHex(response));
    }

    //******************************************************************************************************
//*****************          get information from data.ini and set into garage         *****************
//******************************************************************************************************
    private void getValuesFromFile() {
        try {
            // Read out config.ini file to get the jsonbin configuration
            File f = new File("/home/data.ini");
            Ini ini = new Ini(f);

            if (f.exists()) {
                garage.freeSpacesShortTerm = Integer.parseInt(ini.get("values", "freeSpacesST"));
                garage.freeSpacesLongTerm = Integer.parseInt(ini.get("values", "freeSpacesLT"));
                garage.freeSpacesTotal = garage.freeSpacesShortTerm + garage.freeSpacesLongTerm;
                garage.incomingShortTerm = Integer.parseInt(ini.get("values", "incomingST"));
                garage.incomingLongTerm = Integer.parseInt(ini.get("values", "incomingLT"));
                garage.outgoingShortTerm = Integer.parseInt(ini.get("values", "outgoingST"));
                garage.outgoingLongTerm = Integer.parseInt(ini.get("values", "outgoingLT"));
                garage.statusGarage = Integer.parseInt(ini.get("values", "statusPH"));
                garage.statusSignage = Integer.parseInt(ini.get("values", "statusSignage"));
                garage.statusNightControl = Integer.parseInt(ini.get("values", "nightCTRL"));
                garage.statusManualControl = Integer.parseInt(ini.get("values", "manualCTRL"));

                garage.stationNumber = Integer.parseInt(ini.get("config", "StationNo"));
                garage.lineNumber = Integer.parseInt(ini.get("config", "LineNo"));

                logger.info("Read information from data.ini: Calculated FreeSpacesTotal: " + garage.freeSpacesTotal +
                        " StationNo: " + garage.stationNumber +
                        " LineNo: " + garage.lineNumber +
                        " FreeSpacesShortTerm: " + garage.freeSpacesShortTerm +
                        " FreeSpacesLongTerm: " + garage.freeSpacesLongTerm +
                        " StatusGarage: " + garage.statusGarage +
                        " StatusSignage: " + garage.statusSignage +
                        " StatusManualControl: " + garage.statusManualControl +
                        " StatusNightControl: " + garage.statusNightControl +
                        " incomingST: " + garage.incomingShortTerm +
                        " incomingLT: " + garage.incomingLongTerm +
                        " outgoingST: " + garage.outgoingShortTerm +
                        " outgoingLT: " + garage.outgoingLongTerm);
            } else {
                logger.error("Invalid data.ini file. Exit program.");
                exit(-1);
            }
        } catch (Exception e) {
            logger.error("Error reading file for last values", e);
        }
    }


    //******************************************************************************************************
    //*****************                build new message for 4-byte telegram   SWARCO      *****************
    //******************************************************************************************************
    /* private byte[] buildNewMessageFor4Bytes() {
        getValuesFromFile();

        byte[] response = new byte[27];
        response[0] = 0x02;
        response[1] = 0x31;
        response[2] = 0x01;
        response[3] = (byte) garage.stationNumber;
        response[4] = (byte) (garage.maxFreeSpacesShortTerm >> 8);
        response[5] = (byte) (garage.maxFreeSpacesShortTerm);
        response[6] = (byte) (garage.maxFreeSpacesLongTerm >> 8);
        response[7] = (byte) (garage.maxFreeSpacesLongTerm);
    // Debug-Log zur Überprüfung des gelesenen Werts aus der data.ini
        logger.debug("freeSpacesShortTerm (before setting in response): " + garage.freeSpacesShortTerm);

        // Berechnung der Differenz zwischen Maximalwert und aktuellem Wert für Kurzparker
        int usedShortTermSpaces = garage.maxFreeSpacesShortTerm - garage.freeSpacesShortTerm;

        // Logge die berechnete Zahl
        logger.debug("Used short-term spaces (calculated): " + usedShortTermSpaces);

        // Anzahl aktuell belegter Kurzparker (Bytes 9-10, High Byte und Low Byte)
        response[8] = (byte) (usedShortTermSpaces >> 8); // High Byte
        response[9] = (byte) (usedShortTermSpaces); // Low Byte


        response[10] = (byte) (garage.freeSpacesLongTerm >> 8);
        response[11] = (byte) (garage.freeSpacesLongTerm);
        response[12] = (byte) garage.intervalDay;
        response[13] = (byte) garage.intervalMonth;
        response[14] = (byte) (garage.intervalYear - 2000);
        response[15] = (byte) garage.intervalHour;
        response[16] = (byte) garage.intervalMinute;
        response[17] = (byte) (garage.incomingShortTerm >> 8);
        response[18] = (byte) (garage.incomingShortTerm);
        response[19] = (byte) (garage.outgoingShortTerm >> 8);
        response[20] = (byte) (garage.outgoingShortTerm);
        response[21] = (byte) (garage.incomingLongTerm >> 8);
        response[22] = (byte) (garage.incomingLongTerm);
        response[23] = (byte) (garage.outgoingLongTerm >> 8);
        response[24] = (byte) (garage.outgoingLongTerm);
        response[25] = (byte) garage.statusGarage;
        response[26] = 0x00;
        response[response.length - 2] = 0x03;
        response[response.length - 1] = calculateChecksum(response);

    // Debug-Log für die komplette Antwort
        logger.debug("Complete response message: " + bytesToHex(response));
        return response;
    }
*/

//******************************************************************************************************
//*****************                build new message for 4-byte telegram    SWARCO            *****************
//******************************************************************************************************
    private byte[] buildNewMessageFor4Bytes() {
        getValuesFromFile();
        byte[] response = new byte[28];  // Größe gemäß der neuen Spezifikation (27 Datenbytes + 1 Byte für Checksumme)

        response[0] = 0x02;  // STX

        // Beispiel-Daten aus der data.ini laden und in die entsprechenden Bytes setzen
        response[1] = 0x01; // Antwortkenner (Byte 2)
        response[2] = 0x01; // Anzahl übertragener Zählerbereiche (z.B. 1) (Byte 3)
        response[3] = (byte) garage.stationNumber; // Nummer Zählerbereich 1 (Byte 4)

        // Maximale Anzahl Kurzparker (Bytes 5-6, High Byte und Low Byte)
        response[4] = (byte) (garage.maxFreeSpacesShortTerm >> 8); // High Byte
        response[5] = (byte) (garage.maxFreeSpacesShortTerm);      // Low Byte

        // Maximale Anzahl Dauerparker (Bytes 7-8, High Byte und Low Byte)
        response[6] = (byte) (garage.maxFreeSpacesLongTerm >> 8);  // High Byte
        response[7] = (byte) (garage.maxFreeSpacesLongTerm);       // Low Byte

        // Berechnung der Differenz zwischen Maximalwert und aktuellem Wert für Kurzparker
        int usedShortTermSpaces = garage.maxFreeSpacesShortTerm - garage.freeSpacesShortTerm;

        // Logge die berechnete Zahl
        logger.debug("Used short-term spaces (calculated): " + usedShortTermSpaces);

        // Anzahl aktuell belegter Kurzparker (Bytes 9-10, High Byte und Low Byte)
        response[8] = (byte) (usedShortTermSpaces >> 8);           // High Byte
        response[9] = (byte) (usedShortTermSpaces);                // Low Byte

        // Anzahl aktuell anwesender Dauerparker (Bytes 11-12, High Byte und Low Byte)
        response[10] = (byte) (garage.freeSpacesLongTerm >> 8);    // High Byte
        response[11] = (byte) (garage.freeSpacesLongTerm);         // Low Byte

        // Intervallzeit (Bytes 13-17: Tag, Monat, Jahr, Stunde, Minute)
        response[12] = (byte) garage.intervalDay;                  // Tag
        response[13] = (byte) garage.intervalMonth;                // Monat
        response[14] = (byte) (garage.intervalYear - 2000);        // Jahr (z.B. 24 für 2024)
        response[15] = (byte) garage.intervalHour;                 // Stunde
        response[16] = (byte) garage.intervalMinute;               // Minute

        // Einfahrten Kurzparker im letzten Intervall (Bytes 18-19, High Byte und Low Byte)
        response[17] = (byte) (garage.incomingShortTerm >> 8);     // High Byte
        response[18] = (byte) (garage.incomingShortTerm);          // Low Byte

        // Ausfahrten Kurzparker im letzten Intervall (Bytes 20-21, High Byte und Low Byte)
        response[19] = (byte) (garage.outgoingShortTerm >> 8);     // High Byte
        response[20] = (byte) (garage.outgoingShortTerm);          // Low Byte

        // Einfahrten Dauerparker im letzten Intervall (Bytes 22-23, High Byte und Low Byte)
        response[21] = (byte) (garage.incomingLongTerm >> 8);      // High Byte
        response[22] = (byte) (garage.incomingLongTerm);           // Low Byte

        // Ausfahrten Dauerparker im letzten Intervall (Bytes 24-25, High Byte und Low Byte)
        response[23] = (byte) (garage.outgoingLongTerm >> 8);      // High Byte
        response[24] = (byte) (garage.outgoingLongTerm);           // Low Byte

        // Status Zählerbereich 1 (Byte 25)
        response[25] = (byte) garage.statusGarage;                 // Beispielstatus für Zählerbereich

        // ETX und Checksumme setzen
        response[26] = 0x03;                                       // ETX
        response[27] = calculateChecksumFor28Bytes(response);      // Checksumme berechnen

        // Debug-Log für die komplette Antwort
        logger.debug("Complete response message: " + bytesToHex(response));
        return response;
    }

    // Methode zur Berechnung der Checksumme für die 28-Byte-Antwort
    private byte calculateChecksumFor28Bytes(byte[] data) {
        byte checksum = 0;
        // Berechne die XOR-Checksumme über die ersten 27 Bytes
        for (int i = 0; i < data.length - 1; i++) {
            checksum ^= data[i];
        }
        return checksum;
    }



    //******************************************************************************************************
    //*****************                   build SIEMENS message for 0x32 command           *****************
    //******************************************************************************************************
    private byte[] buildMessageSiemens0x32(byte[] lineNo, byte[] stationNo) {
        byte[] result = new byte[39];
        byte manualControl = IntToByteArray1(garage.statusManualControl)[0];
        byte[] inSTParkers = IntToByteArray4(garage.incomingShortTerm);
        byte[] inLTParkers = IntToByteArray4(garage.incomingLongTerm);
        byte[] outSTParkers = IntToByteArray4(garage.outgoingShortTerm);
        byte[] outLTParkers = IntToByteArray4(garage.outgoingLongTerm);
        byte[] freeSTParkers = IntToByteArray4(garage.freeSpacesShortTerm);
        byte[] freeLTParkers = IntToByteArray4(garage.freeSpacesLongTerm);
        byte[] maxSTParkers = IntToByteArray4(garage.maxFreeSpacesShortTerm);
        byte[] maxLTParkers = IntToByteArray4(garage.maxFreeSpacesLongTerm);
        byte[] failure = new byte[]{0x30, 0x30, 0x30, 0x30};
        byte statusPH = IntToByteArray1(garage.statusGarage)[0];
        byte statusSignage = IntToByteArray1(garage.statusSignage)[0];
        result[0] = manualControl;
        result[1] = inSTParkers[0];
        result[2] = inSTParkers[1];
        result[3] = inSTParkers[2];
        result[4] = inSTParkers[3];
        result[5] = inLTParkers[0];
        result[6] = inLTParkers[1];
        result[7] = inLTParkers[2];
        result[8] = inLTParkers[3];
        result[9] = outSTParkers[0];
        result[10] = outSTParkers[1];
        result[11] = outSTParkers[2];
        result[12] = outSTParkers[3];
        result[13] = outLTParkers[0];
        result[14] = outLTParkers[1];
        result[15] = outLTParkers[2];
        result[16] = outLTParkers[3];
        result[17] = freeSTParkers[0];
        result[18] = freeSTParkers[1];
        result[19] = freeSTParkers[2];
        result[20] = freeSTParkers[3];
        result[21] = freeLTParkers[0];
        result[22] = freeLTParkers[1];
        result[23] = freeLTParkers[2];
        result[24] = freeLTParkers[3];
        result[25] = maxSTParkers[0];
        result[26] = maxSTParkers[1];
        result[27] = maxSTParkers[2];
        result[28] = maxSTParkers[3];
        result[29] = maxLTParkers[0];
        result[30] = maxLTParkers[1];
        result[31] = maxLTParkers[2];
        result[32] = maxLTParkers[3];
        result[33] = failure[0];
        result[34] = failure[1];
        result[35] = failure[2];
        result[36] = failure[3];
        result[37] = statusPH;
        result[38] = statusSignage;
        return result;
    }

    //******************************************************************************************************
    //*****************                build SIEMENS message for 0x34 command               *****************
    //******************************************************************************************************
    private byte[] buildMessageSiemens0x33(byte[] lineNo, byte[] stationNo) {
        byte[] result = new byte[3 + garage.numberOfDisplays * 4];
        if (garage.numberOfDisplays > 8) {
            garage.numberOfDisplays = 8;
        }
        byte numberOfDisplaysByte = IntToByteArray1(garage.numberOfDisplays)[0];
        byte reserve = 0x30;
        byte nightControl = IntToByteArray1(garage.statusNightControl)[0];
        int[] displays = new int[garage.numberOfDisplays];
        for (int i = 0; i < garage.numberOfDisplays; i++) {
            displays[i] = garage.freeSpacesTotal;
        }
        result[0] = numberOfDisplaysByte;
        result[1] = reserve;
        result[2] = nightControl;
        for (int i = 0; i < garage.numberOfDisplays; i++) {
            byte[] tempDisplay = IntToByteArray4(displays[i]);
            for (int j = 0; j < 4; j++) {
                result[3 + i * 4 + j] = tempDisplay[j];
            }
        }
        return result;
    }

    //******************************************************************************************************
    //*****************             Helper Methods (e.g., IntToByteArray)                  *****************
    //******************************************************************************************************
    private byte[] IntToByteArray4(int data) {
        byte[] result = new byte[4];
        String s = String.valueOf(data);
        if (data < 10) {
            result = new byte[]{0x30, 0x30, 0x30, (byte) s.charAt(0)};
        } else if (data < 100) {
            result = new byte[]{0x30, 0x30, (byte) s.charAt(0), (byte) s.charAt(1)};
        } else if (data < 1000) {
            result = new byte[]{0x30, (byte) s.charAt(0), (byte) s.charAt(1), (byte) s.charAt(2)};
        } else if (data < 10000) {
            result = new byte[]{(byte) s.charAt(0), (byte) s.charAt(1), (byte) s.charAt(2), (byte) s.charAt(3)};
        } else {
            logger.warn("Number out of range: " + data);
        }
        logger.debug("Free Value: " + data + " as bytes: " + bytesToHex(result));
        return result;
    }

    private byte[] IntToByteArray1(int data) {
        return new byte[]{(byte) String.valueOf(data).charAt(0)};
    }

    private byte calculateChecksum(byte[] data) {
        byte checksum = 0;
        for (int i = 1; i < data.length - 1; i++) {
            checksum ^= data[i];
        }
        return checksum;
    }

    private byte[] extractLineNumberSiemensPDE(byte data1, byte data2) {
        return new byte[]{data1, data2};
    }

    private byte[] extractStationNumberSiemensPDE(byte data1, byte data2) {
        return new byte[]{data1, data2};
    }

/*
    protected static String bytesToHex(byte[] input) {
        char[] hexChars = new char[input.length * 2];
        for (int j = 0; j < input.length; j++) {
            int v = input[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
*/

/*
    public Garage readInit() {
        // Placeholder for reading initialization settings
        return new Garage();
    }
*/
    private class Garage {
        int lineNumber;
        int stationNumber;
        int freeSpacesShortTerm;
        int freeSpacesLongTerm;
        int maxFreeSpacesShortTerm;
        int maxFreeSpacesLongTerm;
        int freeSpacesTotal;
        int incomingShortTerm;
        int incomingLongTerm;
        int outgoingShortTerm;
        int outgoingLongTerm;
        int intervalDay;
        int intervalMonth;
        int intervalYear;
        int intervalHour;
        int intervalMinute;
        int statusGarage;
        int statusSignage;
        int statusManualControl;
        int statusNightControl;
        int numberOfDisplays;
        int command;
    }
}
