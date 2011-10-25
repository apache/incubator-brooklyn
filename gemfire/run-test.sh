#!/bin/bash

nohup java -cp ./bin/:./lib/antlr.jar:./lib/gemfire.jar brooklyn.gemfire.demo.Server 8081 ./test/resources/us/cache.xml ./log/us-hub.log ./lib/gemfireLicense.zip > ./log/us-hub.out 2> ./log/us-hub.err < /dev/null &
echo $!
nohup java -cp ./bin/:./lib/antlr.jar:./lib/gemfire.jar brooklyn.gemfire.demo.Server 8082 ./test/resources/eu/cache.xml ./log/eu-hub.log ./lib/gemfireLicense.zip > ./log/eu-hub.out 2> ./log/eu-hub.err < /dev/null &
echo $!

sleep 5

wget -qO- "http://localhost:8081/gateway/add?id=EU&endpointId=EU-1&host=localhost&port=33333"
wget -qO- "http://localhost:8082/gateway/add?id=US&endpointId=US-1&host=localhost&port=11111"


# wget -qO- "http://localhost:8081/region/add?name=trades"
# wget -qO- "http://localhost:8082/region/add?name=trades"
