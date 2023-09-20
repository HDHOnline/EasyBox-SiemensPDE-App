#! /bin/bash

############# SiemensPDE
echo "Beginn Setup SiemensPDE App"
# The whole repos was cloned to /home/pi/EasyBox-SiemensPDE-App by JFrog

cd /etc/init.d/
sudo cp /home/pi/EasyBox-SiemensPDE-App/setup/EasyBox-SiemensPDE EasyBox-SiemensPDE
sudo chmod 755 /etc/init.d/EasyBox-SiemensPDE
sudo update-rc.d EasyBox-SiemensPDE defaults
# to delete EasyBox-SiemensPDE script: sudo update-rc.d -f EasyBox-SiemensPDE remove

sudo systemctl disable hciuart

cd /home/pi/
sudo mkdir deploy
cd /home/pi/deploy/
sudo cp /home/pi/EasyBox-SiemensPDE-App/out/artifacts/SiemensPDE_jar/SiemensPDE.jar SiemensPDE.jar
sudo chmod 777 SiemensPDE.jar
sudo chown pi:pi SiemensPDE.jar

# create cronjobs SiemensPDE
cd /etc/cron.5min
sudo cp /home/pi/EasyBox-SiemensPDE-App/setup/EasyBox-cron-SiemensPDE EasyBox-cron-SiemensPDE
sudo chmod 777 /etc/cron.5min/EasyBox-cron-SiemensPDE



# cleanup
cd /home/pi/
sudo rm -r EasyBox-SiemensPDE-App
echo "Ende Setup SiemensPDE App"



