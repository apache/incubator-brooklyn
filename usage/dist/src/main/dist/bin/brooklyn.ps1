#!ps1
#
# Brooklyn
#
# Copyright 2013 by Cloudsoft Corp.
# Licensed under the Apache 2.0 License

$ErrorActionPreference = "Stop";

$ROOT = split-path -parent $MyInvocation.MyCommand.Definition

# discover BROOKLYN_HOME if not set, by attempting to resolve absolute path of this command (brooklyn)
if ( $env:BROOKLYN_HOME -eq $null ) {
    $BROOKLYN_HOME = split-path -parent $ROOT
} else {
    $BROOKLYN_HOME = $env:BROOKLYN_HOME
}

# set up the classpath

$cp = Get-ChildItem ${BROOKLYN_HOME}\conf | Select-Object -ExpandProperty FullName
$cp += Get-ChildItem ${BROOKLYN_HOME}\patch | Select-Object -ExpandProperty FullName
$cp += Get-ChildItem ${BROOKLYN_HOME}\lib | Select-Object -ExpandProperty FullName
$cp += Get-ChildItem ${BROOKLYN_HOME}\dropin | Select-Object -ExpandProperty FullName
$INITIAL_CLASSPATH = $cp -join ';'

# specify additional CP args in BROOKLYN_CLASSPATH
if ( ! ( $env:BROOKLYN_CLASSPATH -eq $null )) {
    $INITIAL_CLASSPATH = "$($INITIAL_CLASSPATH);$($env:BROOKLYN_CLASSPATH)"
}

# start to build up the arguments to the java invocation
$javaargs = @()

# add the user's java opts, or use default memory settings, if not specified
if ( $env:JAVA_OPTS -eq $null ) {
    $javaargs +="-Xms256m -Xmx1g -XX:MaxPermSize=256m"
} else {
    $javaargs +=$env:JAVA_OPTS
}

# force resolution of localhost to be loopback, otherwise we hit problems
# TODO should be changed in code
$javaargs += "-Dbrooklyn.localhost.address=127.0.0.1 $($JAVA_OPTS)"

# workaround for http://bugs.sun.com/view_bug.do?bug_id=4787931
$javaargs += "-Duser.home=$env:USERPROFILE"

# add the classpath
$javaargs += "-cp"
$javaargs += $INITIAL_CLASSPATH

# main class
$javaargs += "brooklyn.cli.Main"

# copy in the arguments that were given to this script
$javaargs += $args

# start Brooklyn
$process = Start-Process -FilePath "java" -ArgumentList $javaargs -NoNewWindow -PassThru

# save PID
Set-Content -Path ${BROOKLYN_HOME}\pid_java -Value $($process.Id)

# wait for it to finish
$process.WaitForExit()
