#!/bin/bash
# /etc/os-release is the new-ish standard for specifying OS details.
# See http://www.freedesktop.org/software/systemd/man/os-release.html.
# There are a multitude of system-dependent files we can check (see list 
# at http://linuxmafia.com/faq/Admin/release-files.html). We can support 
# them as we need.

# Survey of CentOS 6.5, Debian Jessie, Fedora 17, OSX and Ubuntu 12.04 suggests
# uname -m is the most reliable flag for architecture
ARCHITECTURE=$(uname -m)

# Try the standard
if [ -f /etc/os-release ]; then
    source /etc/os-release

# Try RedHat-based systems
elif [ -f /etc/redhat-release ]; then
    NAME=$(cat /etc/redhat-release | cut -d " " -f 1)
    VERSION_ID=$(cat /etc/redhat-release  | cut -d " " -f 3)

# Try OSX
elif command -v sw_vers >/dev/null 2>&1; then
    NAME=$(sw_vers -productName)
    VERSION_ID=$(sw_vers -productVersion)
fi

# Debian os-release doesn't set versions
if [ -z $VERSION_ID ] && [ -f /etc/debian_version ]; then
    VERSION_ID=$(cat /etc/debian_version)
fi

echo $NAME
echo $VERSION_ID
echo $ARCHITECTURE
