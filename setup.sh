#! /bin/bash

############# SiemensPDE

# The whole repos was cloned to /home/pi/EasyBox-SiemensPDE-App by JFrog

cd /etc/init.d/
sudo cp /home/pi/EasyBox-SiemensPDE-App/setup/autostart autostart
sudo chmod 755 /etc/init.d/autostart
sudo update-rc.d autostart defaults
# to delete autostart script: sudo update-rc.d -f autostart remove

sudo systemctl disable hciuart

cd /home/
sudo mkdir logs
sudo chmod 777 /home/logs
sudo chown pi:pi /home/logs

cd /home/pi/
sudo mkdir deploy
cd /home/pi/deploy/
sudo cp /home/pi/EasyBox-SiemensPDE-App/deploy/SiemensPDE.jar SiemensPDE.jar
sudo chmod 777 SiemensPDE.jar
sudo chown pi:pi SiemensPDE.jar

# cleanup
cd /home/pi/
sudo rm -r EasyBox-SiemensPDE-App



