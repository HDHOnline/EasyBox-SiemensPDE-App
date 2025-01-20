# Pi4J V2 :: HDH

[![Code Build Status](https://img.shields.io/github/workflow/status/Pi4J/pi4j-example-crowpi/CrowPi%20CI?label=code)](https://github.com/Pi4J/pi4j-example-crowpi/actions/workflows/crowpi.yml)
[![Docs Build Status](https://img.shields.io/github/checks-status/Pi4J/pi4j-example-crowpi/gh-pages?label=docs)](https://pi4j.com/pi4j-example-crowpi/)
[![License](https://img.shields.io/github/license/Pi4J/pi4j-example-crowpi)](https://github.com/Pi4J/pi4j-example-crowpi/blob/main/LICENSE)

This project contains both example applications and ready-made component classes for interacting with the
[CrowPi](https://www.elecrow.com/crowpi-compact-raspberry-pi-educational-kit.html) using the Pi4J (V2) library. You can easily get started
with electronics programming by testing and modifying the bundled examples or even write your own application.

## COMPONENTS

The provided component classes as part of this library provide an implementation for every available component of the CrowPi. The following
table provides an overview of all supported components with a link to their implementation and example app:


| **Component** | **Example App**                                                                   | **Implementation**                                                                    |
|---------------|-----------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| Buzzer        | [BuzzerApp.java](src/main/java/com/pi4j/crowpi/applications/BuzzerApp.java)       | [BuzzerComponent.java](src/main/java/com/pi4j/crowpi/components/BuzzerComponent.java) |
| SiemensPDE    | [SiemensPDE.java](src/main/java/com/pi4j/crowpi/applications/Serial2Siemens.java) | [Siemens.java](src/main/java/com/pi4j/crowpi/components/SiemensPDE.java)              |

The CrowPi OS image mentioned further down below supports both workarounds out of the box without further configuration.

## CUSTOM OS IMAGE

The Pi4J-team provides several pre-built [custom OS images](https://github.com/Pi4J/pi4j-os). It's highly recommended to use the so called [CrowPi OS](https://pi4j-download.com/main-crowpi.img.zip) for your CrowPi experiments to get the following set of benefits:

- Preconfigured locale (en_US), keyboard (US) and timezone (Europe/Zurich)
- Preconfigured wireless country (Switzerland) by default
- Remote management via SSH and VNC possible without configuration
- Preinstalled OpenJDK 17 and JavaFX to get quickly started
- Preconfigured `/boot/config.txt` which supports all components out of the box
- Dynamic wallpaper which shows Ethernet/WLAN address and hostname
- Comes with `lirc` preinstalled to run the IR receiver component

Download the [latest zip-compressed archive](https://pi4j-download.com/latest.php?flavor=crowpi), extract it and flash it with the imaging tool of your choice to get started.
The default installation provides an user account `pi` with the password `crowpi` and sudo privileges.

## FRAMEWORK

To simplify adding and launching new applications, a custom launcher has been built using PicoCLI.
The [Launcher.java](src/main/java/com/pi4j/crowpi/Launcher.java)
class contains a static list of available targets called `APPLICATIONS` which has to be adjusted when adding new applications to the
project.

By default, an interactive menu gets shown which allows selecting a single target to launch. After executing this target, the application
will automatically end. You may optionally specify the name of an application as the first argument, i.e. `BuzzerApp`, to directly launch
this specific application.

If you want to comfortably test all supported components at once, you may specify the flag `--demo` which will return to the interactive
launcher menu once a target has been executed.

Creating your own applications is as simple as implementing the provided [Application.java](src/main/java/com/pi4j/crowpi/Application.java)
interface, which only requires a single `void execute(Context pi4j)` method.

## BUILD SYSTEM

This project uses Maven for building, testing and running the various applications. While it can be used directly on a Raspberry Pi /
CrowPi, it also supports compiling everything together locally, then pushing the artifacts to the device and running them remotely. The
build system defaults to local deployments, but the following set of Maven properties can be set for remote deployments:

- **`crowpi.remote.host` (required):** Current IP address of the CrowPi, e.g. `192.168.1.2`, used for SCP/SSH
- **`crowpi.remote.port` (optional):** Port to use for SCP/SSH communication, defaults to `22`
- **`crowpi.remote.username` (optional):** Username to use for SCP/SSH, defaults to `pi`
- **`crowpi.remote.password` (optional):** Password to use for SCP/SSH, defaults to `crowpi`
- **`crowpi.remote.target` (optional):** Default directory to temporarily store built artifacts, defaults to `/home/pi/deploy`
- **`crowpi.remote.jvmOptions` (optional):** Additional JVM options, defaults to an empty string

In case of a remote deployment, the artifacts get pushed via SCP and will be automatically executed using SSH. Please note that any existing
files in the deployment folder are being automatically overwritten.

Regardless of which deployment mode you have chosen, the property `crowpi.launcher.args` can be set to specify which arguments should be
passed as-is when running the launcher. This can be used for launching demo mode or directly executing a single application.

## SYSTEM REQUIREMENTS

You may skip this section when using the pre-built CrowPi OS image. Should you choose to run your own image instead, you will need to ensure
that the following lines are present in your `/boot/config.txt`:

```ini
[all]
# Enable X with 128MB GPU memory and support the custom resolution of the CrowPi LCD panel
start_x = 1
gpu_mem = 128
hdmi_cvt 1024 600 60 6 0 0 0

# Enable I2C and SPI
dtparam = i2c_arm=on
dtparam = spi=on

# Enable audio
dtparam = audio=on

# Enable GPIO-IR
dtoverlay = gpio-ir,gpio_pin=20

# Enable DHT11
dtoverlay = dht11,gpiopin=4

# Enable DRM VC4 V3D with up to 2 frame buffers
dtoverlay = vc4-fkms-v3d
max_framebuffers = 2
```

If you want to use the IR receiver and/or humidity/temperature sensor component, you will have to ensure that the required dependencies
mentioned in the "COMPONENTS" section of this README have also been fulfilled.

## RUNTIME DEPENDENCIES

This project has the following runtime dependency requirements:

- [**Pi4J V2**](https://pi4j.com/)
- [**SLF4J (API)**](https://www.slf4j.org/)
- [**SLF4J-SIMPLE**](https://www.slf4j.org/)
- [**PIGPIO Library**](http://abyz.me.uk/rpi/pigpio) (for the Raspberry Pi)

## BUILD AND RUN ON RASPBERRY PI

```shell
$ git clone https://github.com/Pi4J/pi4j-example-crowpi.git
$ cd pi4j-example-crowpi
$ mvn package
$ cd target/distribution/
$ sudo java --module-path . --module com.pi4j.crowpi/com.pi4j.crowpi.Launcher $@

> No application has been specified, defaulting to interactive selection
> Run this launcher with --help for further information
[main] INFO com.pi4j.Pi4J - New context builder
[main] INFO com.pi4j.platform.impl.DefaultRuntimePlatforms - adding platform to managed platform map [id=raspberrypi; name=RaspberryPi Platform; priority=5; class=com.pi4j.crowpi.helpers.CrowPiPlatform]
> The following launch targets are available:
1) Exit launcher without running application
2) Serial2IPApp (com.pi4j.crowpi.applications.Serial2IPApp)
3) Serial2Siemens (com.pi4j.crowpi.applications.Serial2Siemens)
4) BuzzerApp (com.pi4j.crowpi.applications.BuzzerApp)
> Please choose your desired launch target by typing its number:
```

## DOCUMENTATION for setting up environment
- connect github to intelliJ
- recommendation for updates time to time
- https://github.com/HDHOnline/cuddly-broccoli/blob/main/resources/general.sh
- https://github.com/HDHOnline/cuddly-broccoli/blob/main/resources/serial2ip.sh


## DOCUMENTATION for setting up a new Device
![img.png](img.png)
- Setup hardware & OS (Update Script)
- Setup deployment process
- Setting up config.ini and deploy
- Connect a new Device to IOT Manager
- ID & Key
- Select the application to deploy (deployment process)

## DOCUMENTATION for implementing a new Artifact
- connect a artifact to IOT Manager so it can be deployed
- Deployment-Process

## DOCUMENTATION for changing / updating artifacts
- deploy to one or every device?
- how to use and make application changes?

## DOCUMENTATION for Serial2IP
- Requirements for SerialPort on DAZ

from here, serial port event is taking over action

call test function for sending "7" to signages, set lastValue and check TCP connection
this is a visual test-procedure for the people in the field

Todo: implement a test function to inform the IOT Manager of the application deployed successfully


listening on serial port and do handletelegram() if some event happens
1) listening on serial port
2) processing (take values, manipulate, build package)
3) send over TCP to specified RS-IP Tunnels
   at each step the functions catch possible errors and overcomes them at runtime

overall structure:
Component 1: Initialization / Test
Component 2: Processing
Component 3: Communication

Documentation:

The raspberry has to be assembled before shipping (Documentation, and standard image) That also includes a factory test (for serial connection for example).
Burn SD card, plug it in, connect to network
RPi tries to connect to IoT Manager to get Properties like DeviceNumber, Settings, blabla
assign new unconfirmed device to a fleet (what applications should run)
after assigning the fleet, it will run the configurations and commands
after that, f.e. RPi its downloading .json files, keep it locally and update it when needed
local copy is "backup" if no internet connection is available in parking garage or temporary
if there is a update coming in runtime, rerun the application (restart from IOT Manager)

TCP/IP Socket will be build up and closed every time we send to a IP-Address.
Because keeping the connection will cause higher traffic on the LAN network.
That will cause collisions and errors in slow and oly physical networks (like Arnsberg)

If you need to update a new .json file, you need to change the variables in config.ini (please see on set up new device->config.ini)
If you need to make changes to .json, you need to delete the old on local device (maybe cmd-line in IOT?, Process-Cmd)

## DOCUMENTATION for SiemensPDE
- needs USB2Serial converter & "Nullmodem"-cable

## DOCUMENTATION for LEDMatrix-Control
- https://github.com/HDHOnline/cuddly-broccoli/blob/main/resources/general.sh
- https://github.com/HDHOnline/cuddly-broccoli/blob/main/resources/matrix.sh
- see project https://github.com/HDHOnline/cuddly-broccoli/


## DOCUMENTATION Credentials
- github
- jsonbin
- jfrog
- tailscale
- crowpi

- https://www.kp-networks.com/webmailer/?_task=mail&_mbox=mailto%40hdh-onlinehandel.de.Sent
- Usr: developer@hdh-onlinehandel.de
- PW: MaypcugweosBat/

## Helpfull software
- PacketSender
- RealTerm
- Advanced IP Scanner
- Win32 DiskImager
- WinSCP
- Putty

## Handover
- remove tailscale
- remove personalized passwords, etc.
- setup raspberry password
- setup OS
-

## LICENSE

This repository is licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and
limitations under the License.

