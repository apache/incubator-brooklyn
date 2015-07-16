@echo off
REM Licensed to the Apache Software Foundation (ASF) under one
REM or more contributor license agreements.  See the NOTICE file
REM distributed with this work for additional information
REM regarding copyright ownership.  The ASF licenses this file
REM to you under the Apache License, Version 2.0 (the
REM "License"); you may not use this file except in compliance
REM with the License.  You may obtain a copy of the License at
REM 
REM   http://www.apache.org/licenses/LICENSE-2.0
REM 
REM Unless required by applicable law or agreed to in writing,
REM software distributed under the License is distributed on an
REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
REM KIND, either express or implied.  See the License for the
REM specific language governing permissions and limitations
REM under the License.

SETLOCAL EnableDelayedExpansion

REM discover BROOKLYN_HOME if not set, by attempting to resolve absolute path of this command (brooklyn.bat)
IF NOT DEFINED BROOKLYN_HOME (
    SET "WORKING_FOLDER=%~dp0"
    
    REM stript trailing slash
    SET "WORKING_FOLDER=!WORKING_FOLDER:~0,-1!"
    
    REM get parent folder (~dp works only for batch file params and loop indexes)
    FOR %%i in ("!WORKING_FOLDER!") DO SET "BROOKLYN_HOME=%%~dpi"
)

REM Discover the location of Java.
REM Use JAVA_HOME environment variable, if available;
REM else, check the path;
REM else, search registry for Java installations;
REM else fail.

IF DEFINED JAVA_HOME (
    CALL :joinpath "%JAVA_HOME%" bin\java.exe JAVA_BIN
)

IF NOT DEFINED JAVA_BIN (
    IF NOT DEFINED JAVA_HOME CALL :registry_home "HKLM\SOFTWARE\JavaSoft\Java Runtime Environment"
    IF NOT DEFINED JAVA_HOME CALL :registry_home "HKLM\SOFTWARE\Wow6432Node\JavaSoft\Java Runtime Environment"
    IF NOT DEFINED JAVA_HOME CALL :registry_home "HKLM\SOFTWARE\JavaSoft\Java Development Kit"
    IF NOT DEFINED JAVA_HOME CALL :registry_home "HKLM\SOFTWARE\Wow6432Node\JavaSoft\Java Development Kit"
    CALL :joinpath "!JAVA_HOME!" bin\java.exe JAVA_BIN
)

IF NOT DEFINED JAVA_BIN (
    java.exe -version > NUL 2> NUL
    echo !ERRORLEVEL!
    IF NOT !ERRORLEVEL!==9009 SET JAVA_BIN=java.exe
)

IF NOT DEFINED JAVA_BIN (
    echo "Unable to locate a Java installation. Please set JAVA_HOME or PATH environment variables."
    exit /b 1
) ELSE (
    "%JAVA_BIN%" -version > NUL 2> NUL
    IF !ERRORLEVEL!==9009 (
        echo "java.exe does not exist where specified. Please check JAVA_HOME or PATH environment variables."
        exit /b 1
    )
)

REM use default memory settings, if not specified
IF "%JAVA_OPTS%"=="" SET JAVA_OPTS=-Xms256m -Xmx500m -XX:MaxPermSize=256m

REM set up the classpath
SET INITIAL_CLASSPATH=%BROOKLYN_HOME%conf;%BROOKLYN_HOME%lib\patch\*;%BROOKLYN_HOME%lib\brooklyn\*;%BROOKLYN_HOME%lib\dropins\*
REM specify additional CP args in BROOKLYN_CLASSPATH
IF NOT "%BROOKLYN_CLASSPATH%"=="" SET "INITIAL_CLASSPATH=%BROOKLYN_CLASSPATH%;%INITIAL_CLASSPATH%"

REM force resolution of localhost to be loopback, otherwise we hit problems
REM TODO should be changed in code
SET JAVA_OPTS=-Dbrooklyn.location.localhost.address=127.0.0.1 %JAVA_OPTS%

REM workaround for http://bugs.sun.com/view_bug.do?bug_id=4787931
SET JAVA_OPTS=-Duser.home="%USERPROFILE%" %JAVA_OPTS%

REM start Brooklyn
REM NO easy way to find process PID!!!
pushd %BROOKLYN_HOME%

"%JAVA_BIN%" %JAVA_OPTS% -cp "%INITIAL_CLASSPATH%" org.apache.brooklyn.cli.Main %*

popd

ENDLOCAL
GOTO :EOF

:joinpath
    SET Path1=%~1
    SET Path2=%~2
    IF {%Path1:~-1,1%}=={\} (SET "%3=%Path1%%Path2%") ELSE (SET "%3=%Path1%\%Path2%")
GOTO :EOF

:whereis
    REM Doesn't handle paths with quotes in the PATH variable
    SET "%2=%~$PATH:1"
GOTO :EOF

:registry_value
    FOR /F "skip=2 tokens=2*" %%A IN ('REG QUERY %1 /v %2 2^>nul') DO SET "%3=%%B"
GOTO :EOF

:registry_home
    CALL :registry_value %1 CurrentVersion JAVA_VERSION
    CALL :registry_value "%~1\%JAVA_VERSION%" JavaHome JAVA_HOME
GOTO :EOF
