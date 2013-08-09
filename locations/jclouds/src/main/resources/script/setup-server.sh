echo Changing default gateway to ${defaultGateway}
sudo -s -- '/sbin/ip route replace default via ${defaultGateway} dev eth0'
echo Appending nameserver 8.8.8.8 to /etc/resolv.conf
sudo -s -- '/bin/echo nameserver 8.8.8.8 > /etc/resolv.conf'