package com.pi4j.crowpi.components;

import com.fazecast.jSerialComm.*;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pi4j.Pi4J;
import org.ini4j.Ini;
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
    //Serial serial_B;
    SerialPort serial_B;
    Garage garage = readInit();
    String serialBuffer = "";

    //******************************************************************************************************
    //*****************                          constructor SiemensPDE                    *****************
    //*****************                        Main procedure section                      *****************
    //******************************************************************************************************
    // handle Siemens PDE telegram on USB serial port -> serial_B
    public SiemensPDE() {

        var pi4j = Pi4J.newAutoContext();
        // Implement for SIEMENS PDE
        serial_B = SerialPort.getCommPort("/dev/ttyUSB0");
        serial_B.setComPortParameters(9600,8,1,0);

        logger.info("\n" +
                "\n" +
                "- - - System ready - - -\n" +
                "\n");
        logger.info("waiting for serial telegrams.....");

        try{
            serial_B.openPort(); // open the  serial port        //SIEMENS PDE
        }catch (Exception e) {
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

            while(serial_B.isOpen()) {
                var available = serial_B.bytesAvailable();
                if (available > 0) {
                    logger.debug("Available B: " + available);
                    for (int i = 0; i < available; i++) {
                        byte b = (byte) br.read();

                        if (b == 3 && !flag) {    //here we receive the ETX char
                            flag = true;
                            dataBuffer = dataBuffer + (char) b;
                        } else if (flag) {      //here we take the last byte for checksum
                            dataBuffer = dataBuffer + (char) b;
                            handleSiemensPDE(dataBuffer);
                            dataBuffer = "";
                            flag = false;
                        } else {                        //here we are collecting the incoming message
                            dataBuffer = dataBuffer + (char) b;
                        }
                    }
                } else {
                    sleep(10);
                }
            }
        } catch (IOException e) {
            logger.error("IO Exception", e);
        }  catch (InterruptedException e) {
            logger.error("Interrupted Exception", e);
        } //end of try

    } // end of RStoIP

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

        String serialBuffer = "";                           //clear buffer-string
        String ttyInput = input;                          //copy tty input to ttyInput

        logger.info("New Message from SIEMENS PDE: " + bytesToHex(ttyInput.getBytes()));


        //*****************                       analyse serial telegram                      *****************
        serialBuffer += ttyInput; //add received data to serialBuffer

        //remove broken telegrams from beginning
        if (serialBuffer.indexOf(0x02) != 0) {              //check, if Start code is on position 1
            System.out.println("02 not at beginning");
            if (serialBuffer.indexOf(0x02) != -1) {
                serialBuffer = serialBuffer.substring(serialBuffer.indexOf(0x02));  //if Startcode is on other position, take only the string from there and beyond
            } else {       //no Startcode in buffer, so clear buffer and send error-telegram
                System.out.println("clearing buffer");
                serialBuffer = "";
                logger.debug("claring Siemens PDE buffer, cause none 02. ");
            }
        }


        //is telegram in buffer?
        while ((serialBuffer.length() > 0) && (serialBuffer.indexOf(0x02) == 0)) {  //as long as 1F is at beginning (e.g. if more than 1 telegram is in buffer), do:

            int eotPos = serialBuffer.indexOf(0x03);                //eotPos = Position of EOT in the buffer-string

            if (eotPos == -1) {
                break;
            }


            //received telegram is an status telegram
            if (eotPos + 1 == 8) {                 // 03 at even index, so telegram is complete
                byte[] data = serialBuffer.substring(0, eotPos + 2).getBytes();         //take telegram from 02 to 03 in byte Array, called data
                //extract StartCode
                int start = (data[0]);
                logger.debug("Startcode: " + start);
                //extract protocol code
                byte[] lineNo = extractLineNumberSiemensPDE(data[1], data[2]);
                byte[] stationNo = extractStationNumberSiemensPDE(data[3], data[4]);

// mh angepasst zur überpfrfung byte-länge
                byte command = data[5];  // Das Kommando-Byte
logger.info("Received command byte: " + bytesToHex(new byte[]{command}));

                byte[] cmd_tmp = new byte[1];
                cmd_tmp[0] = command;

                byte nightControl = data[6];
                int eot = (data[7]);
                logger.debug("EOT Code: " + eot);
                byte checkSum = data[8];
                //Todo[v2]: do something with the checkSum
                //here the protocol is complete

                getValuesFromFile(); //updates all variables from the saved values in data.ini

                if (command == 0x30) {  // 0x30 -> Status Request with night control
                    logger.info("incoming telegram:" + serialBuffer +
                            "     - valid  Status Request with Command: "+bytesToHex(cmd_tmp));                      // just logging
                    byte[] message = buildMessageSiemens0x32(lineNo, stationNo);
                    logger.debug("message: " + bytesToHex(message));
                    //Todo[v2]: hand over command statement from data.ini (then we have to read it in at getValuesFromFile())? or response based on incoming message?
                    byte[] packageToSend = buildPackageSiemensPDE(lineNo, stationNo, (byte)0x32, message);
                    serial_B.writeBytes(packageToSend, packageToSend.length);
                    logger.info("Response finished: " + bytesToHex(packageToSend));
                }

                else if (command == 0x34) {  // 0x34 -> Status Request
                    logger.info("incoming telegram:" + serialBuffer +
                            "     - valid  Status Request with Command: "+bytesToHex(cmd_tmp));                      // just logging
                    byte[] message = buildMessageSiemens0x33(lineNo, stationNo);
                    logger.debug("message: " + bytesToHex(message));
                    //Todo[v2]: hand over command statement from data.ini (then we have to read it in at getValuesFromFile())? or response based on incoming message?
                    byte[] packageToSend = buildPackageSiemensPDE(lineNo, stationNo, (byte) 0x33, message);
                    serial_B.writeBytes(packageToSend, packageToSend.length);
                    logger.info("Response finished: " + bytesToHex(packageToSend));
                } else {
                    logger.warn("Unsupported Command byte: " + bytesToHex(cmd_tmp)+". Can't handle telegram. Response with NAK(0x15)");
                    byte[] b={(byte)0x15};
                    serial_B.writeBytes(b, 1);
                }

                try {
                    serialBuffer = serialBuffer.substring(eotPos + 1, serialBuffer.length() - 1);   //serial buffer now is only the residual part without first telegram
                } catch (IndexOutOfBoundsException e1) {
                    serialBuffer = "";          //clear buffer
                    break;
                }
            }

        }   //end of while 02 is at beginning

    }   //end of handletelegram

    //******************************************************************************************************
    //*****************          get information from data.ini and set into garage         *****************
    //******************************************************************************************************
    private void getValuesFromFile() {
        try {
            //read out config.ini file to get the jsonbin configuration
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

                logger.info("Readed information from data.ini: Calculated FreeSpacesTotal: "+garage.freeSpacesTotal+
                        " StationNo: "+garage.stationNumber+
                        " LineNo: "+garage.lineNumber+
                        " FreeSpacesShortTerm: "+garage.freeSpacesShortTerm+
                        " FreeSpacesLongTerm: "+garage.freeSpacesLongTerm+
                        " StatusGarage: "+garage.statusGarage+
                        " StatusSignage: "+garage.statusSignage+
                        " StatusManualControl: "+garage.statusManualControl+
                        " StatusNightControl: "+garage.statusNightControl+
                        " incomingST: "+garage.incomingShortTerm+
                        " incomingLT: "+garage.incomingLongTerm+
                        " outgoingST: "+garage.outgoingShortTerm+
                        " outgoingLT: "+garage.outgoingLongTerm);
            } else {
                logger.error("Invalid data.ini file. Exit program.");
                exit(-1);
            }
        } catch (Exception e) {
            logger.error("Error reading file for last values", e);
        }

    }

    //******************************************************************************************************
    //*****************                build SIEMENS telegram with own values              *****************
    //******************************************************************************************************
    private byte[] buildPackageSiemensPDE(byte[] lineNo, byte[] stationNo, byte command, byte[] value) {

        byte[] result = new byte[6 + value.length + 2];

        result[0] = 0x02;               //Start
        result[1] = lineNo[0];          //Line Number  Bit 1
        result[2] = lineNo[1];          //Line Number  Bit 2
        result[3] = stationNo[0];       //Station Number  Bit 1
        result[4] = stationNo[1];       //Station Number  Bit 2
        result[5] = command;            //Command   (0x30: status-request; 0x32: status-response; 0x35: numeric display; 0x37: text-display

        //Databits
        if (value.length - 1 >= 0) System.arraycopy(value, 0, result, 6, value.length);
        result[6 + value.length] = 0x03;              //ETX

        //calculate checksum
        byte[] checksum = new byte[1];

        for (int i = 1; i < result.length - 1; i++) {         //calculate checksum
            checksum[0] ^= result[i];
        }

        result[6 + value.length + 1] = checksum[0];              //CheckSum


        logger.info("calculated outgoing message: "+bytesToHex(result));

        return result;

    }

    //******************************************************************************************************
    //*****************                build SIEMENS message in form of 0x32 command       *****************
    //******************************************************************************************************
    //Todo[v2]: we are not handling lineNo & stationNo here. Add more in data.ini or delete from function?
    private byte[] buildMessageSiemens0x32(byte[] lineNo, byte[] stationNo) {

        byte[] result = new byte[39];

        byte manualControl = IntToByteArray1(garage.statusManualControl)[0];

        int incomingShortTermParkers = garage.incomingShortTerm;
        byte[] inSTParkers = IntToByteArray4(incomingShortTermParkers);
        int incomingLongTermParkers = garage.incomingLongTerm;
        byte[] inLTParkers = IntToByteArray4(incomingLongTermParkers);
        int outgiongShortTermParkers = garage.outgoingShortTerm;
        byte[] outSTParkers = IntToByteArray4(outgiongShortTermParkers);
        int outgiongLongTermParkers = garage.outgoingLongTerm;
        byte[] outLTParkers = IntToByteArray4(outgiongLongTermParkers);

        int freeSpacesShortTermParkers = garage.freeSpacesShortTerm;    //that's what we get by SD and saved in data.ini
        byte[] freeSTParkers = IntToByteArray4(freeSpacesShortTermParkers);
        int freeSpacesLongTermParkers = garage.freeSpacesLongTerm;
        byte[] freeLTParkers = IntToByteArray4(freeSpacesLongTermParkers);

        int maxSpacesShortTermParkers = garage.maxFreeSpacesShortTerm;
        byte[] maxSTParkers = IntToByteArray4(maxSpacesShortTermParkers);
        int maxSpacesLongTermParkers = garage.maxFreeSpacesLongTerm;
        byte[] maxLTParkers = IntToByteArray4(maxSpacesLongTermParkers);

        byte[] failure = new byte[]{0x30, 0x30, 0x30, 0x30};    //Todo[v2]: get from somewhere

        byte statusPH = IntToByteArray1(garage.statusGarage)[0]; // 0x30 -> open; 0x31 -> closed; 0x34 -> automatic (standard);
        byte statusSignage = IntToByteArray1(garage.statusSignage)[0]; //0x30 -> none (standard); 0x31 -> free; 0x32 -> occupied; 0x34 -> closed; 0x38 -> enlighted;

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

        result[17] = freeSTParkers[0];                   // that block the interesting part
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
    //*****************                build SIEMENS message in form of 0x34 command       *****************
    //******************************************************************************************************
    //Todo[v2]: we are not handling lineNo & stationNo here. Add more in data.ini or delete from function?
    private byte[] buildMessageSiemens0x33(byte[] lineNo, byte[] stationNo) {

        byte[] result = new byte[3+garage.numberOfDisplays*4];

        if (garage.numberOfDisplays > 8) {
            garage.numberOfDisplays = 8;
        }
        byte numberOfDisplaysByte = IntToByteArray1(garage.numberOfDisplays)[0];    //0x30 .. 0x38

        byte reserve = 0x30;    //0x30 special functions
        byte nightControl = IntToByteArray1(garage.statusNightControl)[0];    //0x30 lamp on; 0x31 lamp off

        int[] displays = new int[garage.numberOfDisplays];

        for (int i = 0; i <= garage.numberOfDisplays - 1; i++) {
            displays[i] = garage.freeSpacesTotal;
        }

        result[0] = numberOfDisplaysByte;
        result[1] = reserve;
        result[2] = nightControl;

        for (int i = 0; i <= garage.numberOfDisplays - 1; i++) {
            byte[] tempDisplay = IntToByteArray4(displays[i]);
            for (int j = 1; j <= 4; j++) {
                result[2 + i * 4 + j] = tempDisplay[j - 1];
            }
        }

        return result;
    }

    //******************************************************************************************************
    //*****************       Extract SIEMENS PDE Protocol Components: LineNumber          *****************
    //******************************************************************************************************
    private byte[] extractLineNumberSiemensPDE(byte data1, byte data2) {
        //extract rs485(module) adress
        byte[] result = new byte[2];
        result[0] = data1;
        result[1] = data2;
        logger.debug("Siemens PDE LineNumber (hex): " + bytesToHex(result));
        return result;
    }

    //******************************************************************************************************
    //*****************      Extract SIEMENS PDE Protocol Components: StationNumber        *****************
    //******************************************************************************************************
    private byte[] extractStationNumberSiemensPDE(byte data1, byte data2) {
        //extract rs485(module) adress
        byte[] result = new byte[2];
        result[0] = data1;
        result[1] = data2;
        logger.debug("Siemens PDE StationNumber (hex): " + bytesToHex(result));
        return result;
    }

    //******************************************************************************************************
    //*****************                convert int to byte (x.xxx)                         *****************
    //******************************************************************************************************
    private byte[] IntToByteArray4(int data) {
        byte[] result;

        String s = String.valueOf(data);
        byte[] b = new byte[4];

        if (data<10){
            b= new byte[]{0x30, 0x30, 0x30, (byte)s.charAt(0)};
        } else if (data<100){
            b= new byte[]{0x30, 0x30, (byte)s.charAt(0), (byte)s.charAt(1)};
        } else if (data<1000){
            b= new byte[]{0x30, (byte)s.charAt(0), (byte)s.charAt(1), (byte)s.charAt(2)};
        } else if (data<10000){
            b= new byte[]{(byte)s.charAt(0), (byte)s.charAt(1), (byte)s.charAt(2), (byte)s.charAt(3)};
       /* } else if (data<100000){
            b= new byte[]{(byte)s.charAt(0), (byte)s.charAt(1), (byte)s.charAt(2), (byte)s.charAt(3), (byte)s.charAt(4)};
        */
        } else {
            logger.warn("number out of range: " + data);
        }
        result = b;
        logger.debug("Free Value: "+data+ " as bytes: "+bytesToHex(result));
        return result;
    }

    //******************************************************************************************************
    //*****************                convert int to byte (x)                             *****************
    //******************************************************************************************************
    private byte[] IntToByteArray1(int data) {
        byte[] result = new byte[1];
        String s = String.valueOf(data);
        result[0]= (byte)s.charAt(0);
        return result;
    }
}
