---
title: Windows blueprints
layout: website-normal
children:
- re-authentication.md
- stdout-and-stderr.md
---

In addition to controlling UNIX-like servers with SSH, Brooklyn has support for blueprints that deploy to Windows
servers using WinRM to run commands. These deployments can be expressed in pure YAML, and utilise PowerShell to install
and manage the software process.


About WinRM
-----------

WinRM - or *Windows Remote Management* to give its full title - is a system administration service provided in all
recent Windows Server operating systems. It allows remote access to system information (provided via WMI) and the
ability to execute commands. For more information refer to [Microsoft's MSDN article on Windows Remote
Management](https://msdn.microsoft.com/en-us/library/aa384426(v=vs.85).aspx).

WinRM is available by default in Windows Server, but is not enabled by default. Brooklyn will, in most cases, be able
to switch on WinRM support, but this is dependent on your cloud provider supporting a user metadata service with script
execution (see [below](#user-metadata-service-requirement)).


Locations for Windows
---------------------

You must define a new location in Brooklyn for Windows deployments. Windows deployments require a different VM image
ID to Linux, as well as some other special configuration, so you must have separate Brooklyn locations for Windows and
Linux deployments.

In particular, you will most likely want to set these properties on your location:

* `imageId` or `imageNameRegex` - select your preferred Windows Server image from your cloud provider.
* `hardwareId` or `minRam`/`minCores` - since Windows machines generally require more powerful servers, ensure you get
  a machine with the required specification.
* `useJcloudsSshInit` - this must be set to `false`. Without this setting, jclouds will attempt to connect to the new
  VMs using SSH, which will fail on Windows Server.
* `templateOptions` - you may also wish to request a larger disk size. This setting is cloud specific; on AWS, you can
  request a 100GB disk by setting this property to `{mapNewVolumeToDeviceName: ["/dev/sda1", 100, true]}`.

For example, you may place the following in `brooklyn.properties`:

    brooklyn.location.named.AWS\ Oregon\ Win=jclouds:aws-ec2:us-west-2
    brooklyn.location.named.AWS\ Oregon\ Win.displayName = AWS Oregon (Windows)
    brooklyn.location.named.AWS\ Oregon\ Win.imageNameRegex = Windows_Server-2012-R2_RTM-English-64Bit-Base
    brooklyn.location.named.AWS\ Oregon\ Win.hardwareId = m3.medium
    brooklyn.location.named.AWS\ Oregon\ Win.useJcloudsSshInit=false
    brooklyn.location.named.AWS\ Oregon\ Win.templateOptions={mapNewVolumeToDeviceName: ["/dev/sda1", 100, true]}

Alternatively, in your YAML blueprint:

    ...
    location:
      jclouds:aws-ec2:
        region: us-west-2
        identity: AKA_YOUR_ACCESS_KEY_ID
        credential: <access-key-hex-digits>
        imageNameRegex: Windows_Server-2012-R2_RTM-English-64Bit-Base
        hardwareId: m3.medium
        useJcloudsSshInit: false
        templateOptions: {mapNewVolumeToDeviceName: ["/dev/sda1", 100, true]}
    ...


A Sample Blueprint
------------------

Creating a Windows VM is done using the `brooklyn.entity.basic.VanillaWindowsProcess` entity type. This is very similar
to `VanillaSoftwareProcess`, but adapted to work for Windows and WinRM instead of Linux. We suggest you read the
[documentation for VanillaSoftwareProcess](../custom-entities.html#vanilla-software-using-bash) to find out what you
can do with this entity.

For example - here is a blueprint:

    name: Server with 7-Zip

    location:
      jclouds:aws-ec2:
        region: us-west-2
        identity: AKA_YOUR_ACCESS_KEY_ID
        credential: <access-key-hex-digits>
        imageNameRegex: Windows_Server-2012-R2_RTM-English-64Bit-Base
        hardwareId: m3.medium
        useJcloudsSshInit: false
        templateOptions: {mapNewVolumeToDeviceName: ["/dev/sda1", 100, true]}

    services:
    - type: brooklyn.entity.basic.VanillaWindowsProcess
      brooklyn.config:
        templates.install:
          file:///Users/richard/install7zip.ps1: "C:\\install7zip.ps1"
        install.command: powershell -command "C:\\install7zip.ps1"
        customize.command: echo true
        launch.command: echo true
        stop.command: echo true
        checkRunning.command: echo true
        installer.download.url: http://www.7-zip.org/a/7z938-x64.msi

The installation script - referred to as `/Users/richard/install7zip.ps1` in the example above - is:

    $Path = "C:\InstallTemp"
    New-Item -ItemType Directory -Force -Path $Path

    $Url = "${config['installer.download.url']}"
    $Dl = [System.IO.Path]::Combine($Path, "installer.msi")
    $WebClient = New-Object System.Net.WebClient
    $WebClient.DownloadFile( $Url, $Dl )

    Start-Process "msiexec" -ArgumentList '/qn','/i',$Dl -RedirectStandardOutput ( [System.IO.Path]::Combine($Path, "stdout.txt") ) -RedirectStandardError ( [System.IO.Path]::Combine($Path, "stderr.txt") ) -Wait

This is only a very simple example. A core complex example can be found in the [Microsoft SQL Server blueprint in the
Brooklyn source code](https://github.com/apache/incubator-brooklyn/tree/master/software/database/src/main/resources/brooklyn/entity/database/mssql).


Known Limitations and Special Cases
-----------------------------------

### User metadata service requirement

WinRM requires activation and configuration before it will work in a standard Windows Server deployment. To automate
this, Brooklyn will place a setup script in the user metadata blob. Services such as Amazon EC2's `Ec2ConfigService`
will automatically load and execute this script. If your chosen cloud provider does not support `Ec2ConfigService` or
a similar package, or if you cloud provider does not support user metadata, then you must pre-configure a Windows image
with the required WinRM setup and make Brooklyn use this image.

### Use of unencrypted HTTP

Brooklyn is currently using unencrypted HTTP for WinRM communication. This means that the login credentials will be
transmitted in clear text.

In future we aim to improve Brooklyn to support HTTPS. However this does involve issues are certificate creation and
verification.

### Standard output and error streams

These are not made available to Brooklyn like they are on Linux. For a workaround, please refer to [Redirecting
stdout/stderr](stdout-and-stderr.html). We hope to resolve this in a future version of Brooklyn.

### Credentials issue requiring special configuration

It appears that when a script is run over WinRM over HTTP, the credentials under which the script are run are marked as
'remote' credentials, which are prohibited from running certain security-related operations. This may prevent certain
operations. The installer from Microsoft SQL Server is known to fail in this case, for example. For a workaround, please
refer to [Re-authenticating within a PowerShell script](re-authentication.html).

Certain registry keys must be reconfigured in order to support re-authentication. Brooklyn will take care of this at
instance boot time. Please ensure that Brooklyn's changes are compatible with your organisation's security policy.

Re-authentication also requires that the password credentials are passed in plain text in the blueprint's script files.
Please be aware that it is normal for script files - and therefore the plaintext password - to be saved to the VM's
disk.
