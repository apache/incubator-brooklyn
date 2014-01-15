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
    # Example: Red Hat Enterprise Linux Server release 6.3 (Santiago)
    # Match everything up to ' release'
    NAME=$(cat /etc/redhat-release | sed 's/ release.*//')
    # Match everything between 'release ' and the next space
    VERSION_ID=$(cat /etc/redhat-release | sed 's/.*release \([^ ]*\).*/\1/')

# Try OSX
elif command -v sw_vers >/dev/null 2>&1; then
    NAME=$(sw_vers -productName)
    VERSION_ID=$(sw_vers -productVersion)
fi

# Debian os-release doesn't set versions, and Debian 6 doesn't have os-release or lsb_release
if [ -z $VERSION_ID ] && [ -f /etc/debian_version ]; then
    NAME=Debian
    VERSION_ID=$(cat /etc/debian_version)
fi

echo "name:$NAME"
echo "version:$VERSION_ID"
echo "architecture:$ARCHITECTURE"

