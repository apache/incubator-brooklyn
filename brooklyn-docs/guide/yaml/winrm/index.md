---
title: Windows Blueprints
layout: website-normal
---

Brooklyn can deploy to Windows servers using WinRM to run commands. These deployments can be 
expressed in pure YAML, and utilise Powershell to install and manage the software process. 
This approach is similar to the use of SSH for UNIX-like servers.


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

In your YAML blueprint:

    ...
    location:
      jclouds:aws-ec2:
        region: us-west-2
        identity: AKA_YOUR_ACCESS_KEY_ID
        credential: <access-key-hex-digits>
        imageNameRegex: Windows_Server-2012-R2_RTM-English-64Bit-Base-.*
        imageOwner: 801119661308
        hardwareId: m3.medium
        useJcloudsSshInit: false
        templateOptions: {mapNewVolumeToDeviceName: ["/dev/sda1", 100, true]}
    ...

Alternatively, you can define a new named location in `brooklyn.properties`:

    brooklyn.location.named.AWS\ Oregon\ Win = jclouds:aws-ec2:us-west-2
    brooklyn.location.named.AWS\ Oregon\ Win.displayName = AWS Oregon (Windows)
    brooklyn.location.named.AWS\ Oregon\ Win.imageNameRegex = Windows_Server-2012-R2_RTM-English-64Bit-Base-.*
    brooklyn.location.named.AWS\ Oregon\ Win.imageOwner = 801119661308
    brooklyn.location.named.AWS\ Oregon\ Win.hardwareId = m3.medium
    brooklyn.location.named.AWS\ Oregon\ Win.useJcloudsSshInit = false
    brooklyn.location.named.AWS\ Oregon\ Win.templateOptions = {mapNewVolumeToDeviceName: ["/dev/sda1", 100, true]}



A Sample Blueprint
------------------

Creating a Windows VM is done using the `org.apache.brooklyn.entity.software.base.VanillaWindowsProcess` entity type. This is very similar
to `VanillaSoftwareProcess`, but adapted to work for Windows and WinRM instead of Linux. We suggest you read the
[documentation for VanillaSoftwareProcess](../custom-entities.html#vanilla-software-using-bash) to find out what you
can do with this entity.

Entity authors are strongly encouraged to write Windows Powershell or Batch scripts as separate 
files, to configure these to be uploaded, and then to configure the appropriate command as a 
single line that executes given script.

For example - here is a simplified blueprint (but see [Tips and Tricks](#tips-and-tricks) below!):

    name: Server with 7-Zip

    location:
      jclouds:aws-ec2:
        region: us-west-2
        identity: AKA_YOUR_ACCESS_KEY_ID
        credential: <access-key-hex-digits>
        imageNameRegex: Windows_Server-2012-R2_RTM-English-64Bit-Base-.*
        imageOwner: 801119661308
        hardwareId: m3.medium
        useJcloudsSshInit: false
        templateOptions: {mapNewVolumeToDeviceName: ["/dev/sda1", 100, true]}

    services:
    - type: org.apache.brooklyn.entity.software.base.VanillaWindowsProcess
      brooklyn.config:
        templates.preinstall:
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

This is only a very simple example. A more complex example can be found in the [Microsoft SQL Server blueprint in the
Brooklyn source code]({{ site.brooklyn.url.git }}/software/database/src/main/resources/org/apache/brooklyn/entity/database/mssql).


Tips and Tricks
---------------

The best practices for other entities (e.g. using [VanillaSoftwareProcess](../custom-entities.html#vanilla-software-using-bash))
apply for WinRM as well.

### Execution Phases

Blueprint authors are strongly encouraged to provide an implementation for install, launch, stop 
and checkRunning. These are vital for the generic effectors such as stopping and restarting the 
process.

### Powershell

Powershell commands can be supplied using config options such as `launch.powershell.command`.

This is an alternative to supplying a standard batch command using config such as `launch.command`.
For a given phase, only one of the commands (Powershell or Batch) should be supplied.

### Getting the Right Exit Codes

WinRM (or at least the chosen WinRM client!) can return a zero exit code even on error in certain 
circumstances. It is therefore advisable to follow the guidelines below.

*For a given command, write the Powershell or Batch script as a separate multi-command file. 
Upload this (e.g. by including it in the `files.preinstall` configuration). For the configuration
of the given command, execute the file.*

When you have a command inside the powershell script which want to report its non zero exiting, 
please consider adding a check for its exit code after it.
Example:

    & "C:\install.exe"
    If ($lastexitcode -ne 0) {
        exit $lastexitcode
    }

For Powershell files, consider including 

    $ErrorActionPreference = "Stop"

at the start of the file. 
`$ErrorActionPreference` Determines how Windows PowerShell responds to a non-terminating
error (an error that does not stop the cmdlet processing) at the
command line or in a script, cmdlet, or provider, such as the
errors generated by the Write-Error cmdlet.
https://technet.microsoft.com/en-us/library/hh847796.aspx

See [Incorrect Exit Codes](#incorrect-exit-codes) under Known Limitations below.

### Executing Scripts From Batch Commands

In a batch command, you can execute a batch file or Powershell file. For example:

    install.command: powershell -NonInteractive -NoProfile -Command "C:\\install7zip.ps1"

Or alternatively:

    install.command: C:\\install7zip.bat

### Executing Scripts From Powershell

In a Powershell command, you can execute a batch file or Powershell file. There are many ways
to do this (see official Powershell docs). For example:
 
    install.powershell.command: "& C:\\install7zip.ps1"

Or alternatively:

    install.powershell.command: "& C:\\install7zip.bat"

Note the quotes around the command. This is because the "&" has special meaning in a YAML value. 

### Uploading Script and Configuration Files

Often, blueprints will require that (parameterized) scripts and configuration files are available to be copied to the
target VM. These must be URLs resolvable from the Brooklyn instance, or on the Brooklyn classpath. One simple way 
to achieve this is to compile the support files into a .jar, which is then added to AMP's 'dropins' folder. Alternatively, 
an OSGi bundle can be used, referenced from the catalog item. 

Ensure that these scripts end each line with "\r\n", rather than just "\n".

There are two types of file that can be uploaded: plain files and templated files. A plain 
file is uploaded unmodified. A templated file is interpreted as a [FreeMarker](http://freemarker.org) 
template. This supports a powerful set of substitutions. In brief, anything (unescaped) of the form
`${name}` will be substituted, in this case looking up "name" for the value to use.

Templated files (be they configuration files or scripts) gives a powerful way to inject dependent 
configuration when installing an entity (e.g. for customising the install, or for referencing the
connection details of another entity). A common substitution is of the form `${config['mykey']}`. 
This looks up a config key (in this case named "mykey") and will insert the value into the file.
Another common substitution is is of the form `${attribute['myattribute']}` - this looks up the
attribute named "myattribute" of this entity.

Files can be referenced as URLs. This includes support for things like `classpath://mypath/myfile.bat`. 
This looks for the given (fully qualified) resource on the Brooklyn classpath.

The destination for the file upload is specified in the entity's configuration. Note that "\" must
be escaped. For example `"C:\\install7zip.ps1"`.

A list of plain files to be uploaded can be configured under `files.preinstall`, `files.install` and
`files.runtime`. These are uploaded respectively prior to executing the `pre.install.command`,
prior to `install.command` and prior to `pre.launch.command`.

A list of templated files to be uploaded can be configured under `templates.preinstall`, `templates.install`
and `templates.runtime`. The times these are uploaded is as for the plain files. The templates 
substitutions will be resolved only at the point when the file is to be uploaded.

For example:

    files.preinstall:
    - classpath://com/acme/installAcme.ps1
    - classpath://com/acme/acme.conf

### Parameterised Scripts

Calling parameterised Batch and Powershell scripts is done in the normal Windows way - see
offical Microsoft docs. For example:

    install.command: "c:\\myscript.bat myarg1 myarg2"

Or as a Powershell example:

    install.powershell.command: "& c:\\myscript.ps1 -key1 myarg1 -key2 myarg2"

It is also possible to construct the script parameters by referencing attributes of this or
other entities using the standard `attributeWhenReady` mechanism. For example:

    install.command: $brooklyn:formatString("c:\\myscript.bat %s", component("db").attributeWhenReady("datastore.url"))

### Rebooting

Where a reboot is required as part of the entity setup, this can be configured using
config like `pre.install.reboot.required` and `install.reboot.required`. If required, the 
installation commands can be split between the pre-install, install and post-install phases
in order to do a reboot at the appropriate point of the VM setup.

### Install Location

Blueprint authors are encouraged to explicitly specify the full path for file uploads, and 
for paths in their Powershell scripts (e.g. for installation, configuration files, log files, etc).

### How and Why to re-authenticate within a powershell script

Brooklyn will run powershell scripts by making a WinRM call over HTTP. For most scripts this will work, however for
some scripts (e.g. MSSQL installation), this will fail even if the script can be run locally (e.g. by using RDP to
connect to the machine and running the script manually)

For example in the case of MS SQL server installation, there was no clear indication of why this would not work. 
The only clue was a security exception in the installation log.

When a script is run over WinRM over HTTP, the credentials under which the script are run are marked as
'remote' credentials, which are prohibited from running certain security-related operations. The solution was to obtain
a new set of credentials within the script and use those credentials to execute the installer, so this:

    ( $driveLetter + "setup.exe") /ConfigurationFile=C:\ConfigurationFile.ini

became this:

    $pass = '${attribute['windows.password']}'
    $secpasswd = ConvertTo-SecureString $pass -AsPlainText -Force
    $mycreds = New-Object System.Management.Automation.PSCredential ($($env:COMPUTERNAME + "\Administrator"), $secpasswd)

    Start-Process ( $driveLetter + "setup.exe") -ArgumentList "/ConfigurationFile=C:\ConfigurationFile.ini" -Credential $mycreds -RedirectStandardOutput "C:\sqlout.txt" -RedirectStandardError "C:\sqlerr.txt" -Wait

The `$pass=` line simply reads the Windows password from the entity before the script is copied to the server. This is
then encrypted on the next line before being used to create a new credential object. Then, rather than calling the executable
directly, the `Start-Process` scriptlet is used. This allows us to pass in the newly created credentials, under which
the process will be run.

Certain registry keys must be reconfigured in order to support re-authentication. Brooklyn will take care of this at
instance boot time, as part of the setup script. Please ensure that Brooklyn's changes are compatible with your 
organisation's security policy.

Re-authentication also requires that the password credentials are passed in plain text in the blueprint's script files.
Please be aware that it is normal for script files - and therefore the plaintext password - to be saved to the VM's
disk.

### Windows AMIs on AWS

Windows AMIs in AWS change regularly (to include the latest Windows updates). If using the community
AMI, it is recommended to use an AMI name regex, rather than an image id, so that the latest AMI is 
always picked up. If an image id is used, it may fail as Amazon will delete their old Windows AMIs.

If using an image regex, it is recommended to include the image owner in case someone else uploads
a similarly named AMI. For example:

    brooklyn.location.named.AWS\ Oregon\ Win = jclouds:aws-ec2:us-west-2
    brooklyn.location.named.AWS\ Oregon\ Win.imageNameRegex = Windows_Server-2012-R2_RTM-English-64Bit-Base-.*
    brooklyn.location.named.AWS\ Oregon\ Win.imageOwner = 801119661308
    ...

## stdout and stderr in a Powershell script

When calling an executable in a Powershell script, the stdout and stderr will usually be output to the console.
This is captured by Brooklyn, and shown in the activities view under the specific tasks.

An alternative is to redirect stdout and stderr to a file on the VM, which can be helpful if one expects sys admins
to log into the VM. However, be warned that this would hide the stdout/stderr from Brooklyn's activities view.

For example, instead of running the following:

    D:\setup.exe /ConfigurationFile=C:\ConfigurationFile.ini

 The redirect can be achieved by using the `Start-Process` scriptlet:

    Start-Process D:\setup.exe -ArgumentList "/ConfigurationFile=C:\ConfigurationFile.ini" -RedirectStandardOutput "C:\sqlout.txt" -RedirectStandardError "C:\sqlerr.txt" -PassThru -Wait

The `-ArgumentList` is simply the arguments that are to be passed to the executable, `-RedirectStandardOutput` and
`RedirectStandardError` take file locations for the output (if the file already exists, it will be overwritten). The
`-PassThru` argument indicates that Powershell should write to the file *in addition* to the console, rather than
*instead* of the console. The `-Wait` argument will cause the scriptlet to block until the process is complete.

Further details can be found on the [Start-Process documentation page](https://technet.microsoft.com/en-us/library/hh849848.aspx)
on the Microsoft TechNet site.


Troubleshooting
---------------

Much of the [operations troubleshooting guide](../../ops/troubleshooting/) is applicable for Windows blueprints.  

### User metadata service requirement

WinRM requires activation and configuration before it will work in a standard Windows Server deployment. To automate
this, Brooklyn will place a setup script in the user metadata blob. Services such as Amazon EC2's `Ec2ConfigService`
will automatically load and execute this script. If your chosen cloud provider does not support `Ec2ConfigService` or
a similar package, or if your cloud provider does not support user metadata, then you must pre-configure a Windows image
with the required WinRM setup and make Brooklyn use this image.

If the configuration options `userMetadata` or `userMetadataString` are used on the location, then this will override
the default setup script. This allows one to supply a custom setup script. However, if userMetadata contains something
else then the setup will not be done and the VM may not not be accessible remotely over WinRM.

### Credentials issue requiring special configuration

When a script is run over WinRM over HTTP, the credentials under which the script are run are marked as
'remote' credentials, which are prohibited from running certain security-related operations. This may prevent certain
operations. The installer from Microsoft SQL Server is known to fail in this case, for example. For a workaround, please
refer to [How and Why to re-authenticate withing a powershell script](#how-and-why-to-re-authenticate-within-a-powershell-script) above.

### AMIs not found

If using the imageId of a Windows community AMI, you may find that the AMI is deleted after a few weeks.
See [Windows AMIs on AWS](#windows-amis-on-aws) above.

### VM Provisioning Times Out

In some environments, provisioning of Windows VMs can take a very long time to return a usable VM.
If the image is old, it may install many security updates (and reboot several times) before it is
usable.

On a VMware vCloud Director environment, the guest customizations can cause the VM to reboot (sometimes
several times) before the VM is usable.

This could cause the WinRM connection attempts to timeout. The location configuration option 
`waitForWinRmAvailable` defaults to `30m` (i.e. 30 minutes). This can be increased if required.

### Windows log files

Details of the commands executed, and their results, can be found in the Brooklyn log and in the Brooklyn 
web-console's activity view. 

There will also be log files on the Windows Server. System errors in Windows are usually reported in the Windows Event Log -  
see [https://technet.microsoft.com/en-us/library/cc766042.aspx](https://technet.microsoft.com/en-us/library/cc766042.aspx) 
for more information.

Additional logs may be created by some Windows programs. For example, MSSQL creates a log in 
`%programfiles%\Microsoft SQL Server\130\Setup Bootstrap\Log\` - for more information see 
[https://msdn.microsoft.com/en-us/library/ms143702.aspx](https://msdn.microsoft.com/en-us/library/ms143702.aspx).


Known Limitations
-----------------

### Use of Unencrypted HTTP

Brooklyn is currently using unencrypted HTTP for WinRM communication. This means that the login credentials will be
transmitted in clear text.

In future we aim to improve Brooklyn to support HTTPS. However this requires adding support to the underlying 
WinRM library, and also involves certificate creation and verification.

### Incorrect Exit Codes

Some limitations with WinRM (or at least the chosen WinRM Client!) are listed below:

##### Single-line Powershell files

When a Powershell file contains just a single command, the execution of that file over WinRM returns exit code 0
even if the command fails! This is the case for even simple examples like `exit 1` or `thisFileDoesNotExist.exe`.

A workaround is to add an initial command, for example:

    Write-Host dummy line for workaround 
    exit 1

##### Direct Configuration of Powershell commands

If a command is directly configured with Powershell that includes `exit`, the return code over WinRM
is not respected. For example, the command below will receive an exit code of 0.

    launch.powershell.command: |
      echo first
      exit 1

##### Direct Configuration of Batch commands

If a command is directly configured with a batch exit, the return code over WinRM
is not respected. For example, the command below will receive an exit code of 0.

    launch.command: exit /B 1

##### Non-zero Exit Code Returned as One

If a batch or Powershell file exits with an exit code greater than one (or negative), this will 
be reported as 1 over WinRM.

We advise you to use native commands (non-powershell ones) since executing it as a native command
will return the exact exit code rather than 1.
For instance if you have installmssql.ps1 script use `install.command: powershell -command "C:\\installmssql.ps1"`
rather than using `install.powershell.command: "C:\\installmssql.ps1"`
The first will give you an exact exit code rather than 1

### PowerShell "Preparing modules for first use"

The first command executed over WinRM has been observed to include stderr saying "Preparing 
modules for first use", such as that below:

    < CLIXML
    <Objs Version="1.1.0.1" xmlns="http://schemas.microsoft.com/powershell/2004/04"><Obj S="progress" RefId="0"><TN RefId="0"><T>System.Management.Automation.PSCustomObject</T><T>System.Object</T></TN><MS><I64 N="SourceId">1</I64><PR N="Record"><AV>Preparing modules for first use.</AV><AI>0</AI><Nil /><PI>-1</PI><PC>-1</PC><T>Completed</T><SR>-1</SR><SD> </SD></PR></MS></Obj><Obj S="progress" RefId="1"><TNRef RefId="0" /><MS><I64 N="SourceId">2</I64><PR N="Record"><AV>Preparing modules for first use.</AV><AI>0</AI><Nil /><PI>-1</PI><PC>-1</PC><T>Completed</T><SR>-1</SR><SD> </SD></PR></MS></Obj></Objs>

The command still succeeded. This has only been observed on private clouds (e.g. not on
AWS). It could be related to the specific Windows images in use. It is recommended that 
VM images are prepared carefully, e.g. so that security patches are up-to-date and the
VM is suitably initialised.

### WinRM executeScript failed: httplib.BadStatusLine: ''

As described in https://issues.apache.org/jira/browse/BROOKLYN-173, a failure has been
observed where the 10 attempts to execute the command over WinRM failed with:

    httplib.BadStatusLine: ''

Subsequently retrying the command worked. It is unclear what caused the failure, but could 
have been that the Windows VM was not yet in the right state.

One possible workaround is to ensure the Windows VM is in a good state for immediate use (e.g. 
security updates are up-to-date). Another option is to increase the number of retries, 
which defaults to 10. This is a configuration option on the machine location, so can be set on
the location's brooklyn.properties or in the YAML: 

    execTries: 20

### Direct Configuration of Multi-line Batch Commands Not Executed

If a command is directly configured with multi-line batch commands, then only the first line 
will be executed. For example the command below will only output "first":

    launch.command: |
      echo first
      echo second

The workaround is to write a file with the batch commands, have that file uploaded, and execute it.

Note this is not done automatically because that could affect the capture and returning
of the exit code for the commands executed.

### Install location

Work is required to better configure a default install location on the VM (e.g. so that 
environment variables are set). The installation pattern for linux-based blueprints,
of using brooklyn-managed-processes/installs, is not used or recommended on Windows.
Files will be uploaded to C:\ if no explicit directory is supplied, which is untidy, 
unnecessarily exposes the scripts to the user, and could cause conflicts if multiple 
entities are installed.

Blueprint authors are strongly encourages to explicitly specific directories for file
uploads and in their Powershell scripts.
