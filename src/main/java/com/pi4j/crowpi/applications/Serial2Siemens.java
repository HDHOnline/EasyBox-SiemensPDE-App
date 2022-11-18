package com.pi4j.crowpi.applications;

import com.pi4j.context.Context;
import com.pi4j.crowpi.Application;
import com.pi4j.crowpi.components.SiemensPDE;

public class Serial2Siemens implements Application {

    @Override
    public void execute(Context pi4j) {

        //call SiemensPDE main function to create application
        // SiemensPDE needs a USB2Serial Converter
        final SiemensPDE blackbox = new SiemensPDE();
        //from here, serial port event is taking over action


        // continuous loop to keep the program running until the user terminates the program
        for (; ; ) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}