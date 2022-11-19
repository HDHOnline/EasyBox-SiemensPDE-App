#! /bin/bash

############# SiemensPDE

# The whole repos was cloned to /home/pi/EasyBox-SiemensPDE-App by JFrog

cd /etc/init.d/
sudo cp /home/pi/EasyBox-SiemensPDE-App/setup/EasyBox-SiemensPDE SiemensPDE
sudo chmod 755 /etc/init.d/EasyBox-SiemensPDE
sudo update-rc.d EasyBox-SiemensPDE defaults
# to delete EasyBox-SiemensPDE script: sudo update-rc.d -f EasyBox-SiemensPDE remove

sudo systemctl disable hciuart

cd /home/
sudo mkdir logs
sudo chmod 777 /home/logs
sudo chown pi:pi /home/logs

cd /home/pi/
sudo mkdir deploy
cd /home/pi/deploy/
sudo cp /home/pi/EasyBox-SiemensPDE-App/out/artifacts/SiemensPDE_jar/SiemensPDE.jar SiemensPDE.jar
sudo chmod 777 SiemensPDE.jar
sudo chown pi:pi SiemensPDE.jar

# cleanup
cd /home/pi/
sudo rm -r EasyBox-SiemensPDE-App



