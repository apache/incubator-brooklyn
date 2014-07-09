Brooklyn CLI
============

This is the Command Line Interface that you can use to interact with Brooklyn.

Commands available
==================

```
$ brooklyn help
usage: brooklyn [(-v | --verbose)] [(-q | --quiet)] <command> [<args>]

The most commonly used brooklyn commands are:
    help     Display help information
    launch   Starts a brooklyn application. Note that a BROOKLYN_CLASSPATH environment variable needs to be set up beforehand to point to the user application classpath.

See 'brooklyn help <command>' for more information on a specific command.
```

Launch command options
======================

```
$ brooklyn help launch
NAME
        brooklyn launch - Starts a brooklyn application. Note that a
        BROOKLYN_CLASSPATH environment variable needs to be set up beforehand to
        point to the user application classpath.

SYNOPSIS
        brooklyn [(-q | --quiet)] [(-v | --verbose)] launch
                [(-s <script URI> | --script <script URI>)] [(-nc | --noConsole)] [(-l <location list> | --location <location list> | --locations <location list>)...]
                (-a <application class or file> | --app <application class or file>)
                [(-p <port number> | --port <port number>)]

OPTIONS
        -a <application class or file>, --app <application class or file>
            The Application to start. For example my.AppName or
            file://my/AppName.groovy or classpath://my/AppName.groovy

        -l <location list>, --location <location list>, --locations <location
        list>
            Specifies the locations where the application will be launched.

        -nc, --noConsole
            Whether to start the web console

        -p <port number>, --port <port number>
            Specifies the port to be used by the Brooklyn Management Console.

        -q, --quiet
            Quiet mode

        -s <script URI>, --script <script URI>
            EXPERIMENTAL. URI for a Groovy script to parse and load. This script
            will run before starting the app.

        -v, --verbose
            Verbose mode
```

Example usage
=============

Launching one of the examples on localhost:
```
$ BROOKLYN_HOME=/path/to/brooklyn
$ PATH=$PATH:${BROOKLYN_HOME}/usage/dist/brooklyn-0.4.0-SNAPSHOT-dist/bin              # use right BROOKLYN_VERSION
$ export BROOKLYN_CLASSPATH=${BROOKLYN_HOME}/examples/simple-web-cluster/target/classes/   # point this to your app
$ brooklyn launch --app brooklyn.demo.SingleWebServerExample            # --location is set to localhost by default
```

----
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.