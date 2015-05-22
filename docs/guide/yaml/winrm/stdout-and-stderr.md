---
title: Redirecting stdout and stderr
title_in_menu: Redirecting stdout/stderr
layout: website-normal
---

## Redirecting stdout and stderr in a powershell script

When calling an executable in a powershell script, the stdout and stderr will usually be output to the console,
which is not currently captured by Brooklyn. In order to facilitate debugging, it is usually possible to redirect
stdout and stderr to a file by using the Start-Process scriptlet. So instead of running the following:

```
D:\setup.exe /ConfigurationFile=C:\ConfigurationFile.ini
```

You would run the following:

```
Start-Process D:\setup.exe -ArgumentList "/ConfigurationFile=C:\ConfigurationFile.ini" -RedirectStandardOutput "C:\sqlout.txt" -RedirectStandardError "C:\sqlerr.txt" -PassThru -Wait
```

The -ArgumentList is simply the arguments that are to be passed to the executable, -RedirectStandardOutput and -RedirectStandardError take file locations for the output (if
the file already exists, it will be overwritten). The -PassThru argument indicates that PowerShell should write to the file *in addition* to the console, rather than *instead* of the console.
The -Wait augument will cause the scriptlet to block until the process is complete

Further details can be found here: https://technet.microsoft.com/en-us/library/hh849848.aspx
