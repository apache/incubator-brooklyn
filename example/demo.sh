#!/bin/bash
set -x

ROOT=$(cd $(dirname $0) && pwd)

cd $ROOT && java -Xmx256m -Xmx512m -XX:MaxPermSize=256m -cp target/brooklyn-example-0.0.1-SNAPSHOT-with-dependencies.jar brooklyn.demo.Demo
