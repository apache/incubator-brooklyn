---
layout: website-normal
title: Troubleshooting SoftwareProcess Entities
toc: /guide/toc.json
---

The [troubleshooting overview](overview.html) in Brooklyn gives 
information for how to find more information about errors.

If that doesn't give enough information to diagnose, fix or workaround the problem, then it can be required
to login to the machine, to investigate further. This guide applies to entities that are types
of "SoftwareProcess" in Brooklyn, or that follows those conventions.


## VM connection details

The ssh connection details for an entity is published to a sensor `host.sshAddress`. The login 
credentials will depend on the Brooklyn configuration. The default is to use the `~/.ssh/id_rsa` 
or `~/.ssh/id_dsa` on the Brooklyn host (uploading the associated `~/.ssh/id_rsa.pub` to the machine's 
authorised_keys). However, this can be overridden (e.g. with specific passwords etc) in the 
location's configuration.

For Windows, there is a similar sensor with the name `host.winrmAddress`. (TODO sensor for password?) 


## Install and Run Directories

For ssh-based software processes, the install directory and the run directory are published as sensors
`install.dir` and `run.dir` respectively.

For some entities, files are unpacked into the install dir; configuration files are written to the
run dir along with log files. For some other entities, these directories may be mostly empty - 
e.g. if installing RPMs, and that software writes its logs to a different standard location.

Most entities have a sensor `log.location`. It is generally worth checking this, along with other files
in the run directory (such as console output).


## Process and OS Health

It is worth checking that the process is running, e.g. using `ps aux` to look for the desired process.
Some entities also write the pid of the process to `pid.txt` in the run directory.

It is also worth checking if the required port is accessible. This is discussed in the guide 
"Troubleshooting Server Connectivity Issues in the Cloud", including listing the ports in use:
execute `netstat -antp` (or on OS X `netstat -antp TCP`) to list the TCP ports in use (or use
`-anup` for UDP).

It is also worth checking the disk space on the server, e.g. using `df -m`, to check that there
is sufficient space on each of the required partitions.
