package com.pi4j.crowpi.components;

public class Garage {
    int lineNumber;
    int stationNumber;
    int freeSpacesTotal;
    int maxFreeSpacesShortTerm;
    int maxFreeSpacesLongTerm;
    int statusGarage;
    int statusSignage;
    int statusManualControl;
    int statusNightControl;
    int command;
    int numberOfDisplays;
    int lastValue;


    int freeSpacesShortTerm;
    int freeSpacesLongTerm;
    int incomingShortTerm;
    int incomingLongTerm;
    int outgoingShortTerm;
    int outgoingLongTerm;

    //******************************************************************************************************
    //*****************                 initialize Garage and set factory-value            *****************
    //******************************************************************************************************
    public Garage() {
        lineNumber = 1;
        stationNumber = 1;
        freeSpacesTotal = 9999;
        maxFreeSpacesShortTerm = 9999;
        maxFreeSpacesLongTerm = 0;
        statusGarage = 4;  //0: open; 1: closed; 4: automatic (default)
        statusSignage = 0;  //0: none (default); 1: free; 2: occupied; 4: closed; 8: englighted
        statusManualControl = 0;   //0: default
        statusNightControl = 0; //0: lamp on (default); 1: lamp off
        command = 0;
        numberOfDisplays = 1;
        lastValue = 7;

        freeSpacesShortTerm = 1000;
        freeSpacesLongTerm = 0;
        incomingShortTerm = 0;
        incomingLongTerm = 0;
        outgoingShortTerm = 0;
        outgoingLongTerm = 0;
    }
}