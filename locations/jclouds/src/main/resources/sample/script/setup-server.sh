echo Appending nameserver 8.8.8.8 to /etc/resolv.conf
sudo -s -- '/bin/echo nameserver 8.8.8.8 >> /etc/resolv.conf'
unset OS
OS=`head -1 /etc/issue | awk '{ print $1 }'`
unset OS_MAJOR
OS_MAJOR=`head -1 /etc/issue | awk '{ print $4 }' | cut -d'.' -f1`
if [ $OS = "CentOS" -a $OS_MAJOR -eq 6 ]; then
    echo Appending options single-request-reopen to /etc/resolv.conf
    sudo -s -- '/bin/echo options single-request-reopen >> /etc/resolv.conf'
fi
echo Changing default gateway to ${defaultGateway}
sudo -s -- '/sbin/ip route replace default via ${defaultGateway} dev eth0'