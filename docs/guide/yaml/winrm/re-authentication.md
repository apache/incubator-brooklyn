---
title: Re-authenticating within a PowerShell script
title_in_menu: Re-authentication
layout: website-normal
---

## How and Why to re-authenticate withing a powershell script

Brooklyn will run powershell scripts by making a WinRM call over HTTP. For most scripts this will work, however for
some scripts (e.g. MSSQL installation), this will fail even if the script can be run locally (e.g. by using RDP to
connect to the machine and running the script manually)

In the case of MS SQL server installation, there was no clear indication of why this would not work. The only clue was
a security exception in the installation log

It appears that when a script is run over WinRM over HTTP, the credentials under which the script are run are marked as
'remote' credentials, which are prohibited from running certain security-related operations. The solution was to obtain
a new set of credentials within the script and use those credentials to exeute the installer, so this:

    ( $driveLetter + "setup.exe") /ConfigurationFile=C:\ConfigurationFile.ini

became this:

    $pass = '${attribute['windows.password']}'
    $secpasswd = ConvertTo-SecureString $pass -AsPlainText -Force
    $mycreds = New-Object System.Management.Automation.PSCredential ($($env:COMPUTERNAME + "\Administrator"), $secpasswd)

    Invoke-Command -ComputerName localhost -credential $mycreds -scriptblock {
        param($driveLetter)
        Start-Process ( $driveLetter + "setup.exe") -ArgumentList "/ConfigurationFile=C:\ConfigurationFile.ini" -RedirectStandardOutput "C:\sqlout.txt" -RedirectStandardError "C:\sqlerr.txt" -Wait
    } -Authentication CredSSP -argumentlist $driveLetter

The `$pass=` line simply reads the Windows password from the entity before the script is copied to the server. This is
then encrypted on the next line before being used to create a new credential object. Then, rather than calling the executable
directly, the `Start-Process` scriptlet is used. This allows us to pass in the newly created credentials, under which
the process will be run
