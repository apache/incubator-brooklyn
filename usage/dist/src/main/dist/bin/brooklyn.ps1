#!ps1
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
# Brooklyn
#

$ErrorActionPreference = "Stop";

$ROOT = split-path -parent $MyInvocation.MyCommand.Definition

# discover BROOKLYN_HOME if not set, by attempting to resolve absolute path of this command (brooklyn)
if ( $env:BROOKLYN_HOME -eq $null ) {
    $BROOKLYN_HOME = split-path -parent $ROOT
} else {
    $BROOKLYN_HOME = $env:BROOKLYN_HOME
}

# Discover the location of Java.
# Use JAVA_HOME environment variable, if available;
# else, search registry for Java installations;
# else, check the path;
# else fail.
$bin = [System.IO.Path]::Combine("bin", "java.exe")
if ( $env:JAVA_HOME -ne $null ) {
    $javahome = $env:JAVA_HOME
    $javabin = [System.IO.Path]::Combine($javahome, $bin)
}
if ( $javabin -eq $null ) {
    $reglocations = ( 'HKLM:\SOFTWARE\JavaSoft\Java Runtime Environment',
                      'HKLM:\SOFTWARE\Wow6432Node\JavaSoft\Java Runtime Environment' )
    $jres = @{}
    foreach ($loc in $reglocations) {
        $item = Get-Item $loc -ErrorAction SilentlyContinue
        if ($item -eq $null) { continue }
        foreach ($key in Get-ChildItem $loc) {
            $version = $key.PSChildName
            $jrehome = $key.GetValue("JavaHome")
            $jres.Add($version, $jrehome)
        }
    }
    # TODO - this does a simple sort on the registry key name (the JRE version). This is not ideal - better would be
    # to understand semantic versioning, filter out incompatible JREs (1.5 and earlier), prefer known good JREs (1.6
    # or 1.7) and pick the highest patchlevel.
    $last = ( $jres.Keys | Sort-Object | Select-Object -Last 1 )
    if ( $last -ne $null ) {
        $javahome = $jres.Get_Item($last)
        $javabin = [System.IO.Path]::Combine($javahome, $bin)
    }
}
if ( $javabin -eq $null ) {
    $where = Get-Command "java.exe" -ErrorAction SilentlyContinue
    if ( $where -ne $null ) {
        $javabin = $where.Definition
        $bindir = [System.IO.Path]::GetDirectoryName($javabin)
        $javahome = [System.IO.Path]::GetDirectoryName($bindir)
    }
}

if ( $javabin -eq $null ) {
    throw "Unable to locate a Java installation. Please set JAVA_HOME or PATH environment variables."
} elseif ( $( Get-Item $javabin -ErrorAction SilentlyContinue ) -eq $null ) {
    throw "java.exe does not exist where specified. Please check JAVA_HOME or PATH environment variables."
}

# set up the classpath
$cp = Get-ChildItem ${BROOKLYN_HOME}\conf | Select-Object -ExpandProperty FullName

if ( Test-Path ${BROOKLYN_HOME}\patch ) {
    $cp += Get-ChildItem ${BROOKLYN_HOME}\patch | Select-Object -ExpandProperty FullName
}

$cp += Get-ChildItem ${BROOKLYN_HOME}\lib\brooklyn | Select-Object -ExpandProperty FullName

if ( Test-Path ${BROOKLYN_HOME}\dropins ) {
    $cp += Get-ChildItem ${BROOKLYN_HOME}\dropins | Select-Object -ExpandProperty FullName
}

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
$javaargs += "-Duser.home=`"$env:USERPROFILE`""

# add the classpath
$javaargs += "-cp"
$javaargs += "`"$($INITIAL_CLASSPATH)`""

# main class
$javaargs += "brooklyn.cli.Main"

# copy in the arguments that were given to this script
$javaargs += $args

# start Brooklyn
$process = Start-Process -FilePath $javabin -ArgumentList $javaargs -NoNewWindow -PassThru

# save PID
Set-Content -Path ${BROOKLYN_HOME}\pid_java -Value $($process.Id)

# wait for it to finish
$process.WaitForExit()
