<?xml version="1.0" encoding="UTF-8"?>
<!--
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
-->
<catalog>
  <name>My Local Catalog</name>
  <!-- scan means it will load templates based on @CatalogTemplate annotations on entities;
       recommended only for local elements to prevent having to download JARs just to populate catalog.
       (from a local scan you can generate the XML for publishing by saying  
        `mgmt.getCatalog().toXmlString()` in JS gui groovy page, then tweaking.)  
       also, for a local (non-linked) reference, an empty classpath means to use the default classpath. -->
  <classpath scan="types"/>

  <!-- now (for illustration) we define some other sources which weren't on our classpath but
       which we want included in our catalog on our brooklyn server -->
  <catalog>
    <classpath>
      <entry>file://~/.m2/repository/io/cloudsoft/mapr/brooklyn-mapr/0.0.1-SNAPSHOT/brooklyn-mapr-0.0.1-SNAPSHOT.jar</entry>
    </classpath>
    <!-- templates explicitly listed since we didn't scan above (NB scan=false is the default) -->
    <template type="io.brooklyn.mapr.M3App" name="MapR M3">
        <description>MapR Apache Hadoop M3 resizable cluster deployed to a wide variety of clouds</description>
        <iconUrl>http://releng3.cloudsoftcorp.com/downloads/brooklyn/img/mapr_logo.png</iconUrl>
    </template>
  </catalog>
  
  <catalog>
    <description>Extra local jars I've got on my machine, added so I can pull in the CDH easily.</description>
    <classpath scan="annotations">
      <entry>file://~/.m2/repository/io/cloudsoft/cloudera/brooklyn-cdh/1.0.0-SNAPSHOT/brooklyn-cdh-1.0.0-SNAPSHOT.jar</entry>
      <entry>file://~/.m2/repository/com/cloudera/whirr-cm/1.1-SNAPSHOT/whirr-cm-1.1-SNAPSHOT.jar</entry>
    </classpath>
    <!-- templates here were autodetected (scan=true), so we don't _need_ to list any entities;
         but here we illustrate how we can add our own (or even override, if we left out id) -->
    <template type="io.cloudsoft.cloudera.SampleClouderaManagedCluster" id="my_cdh" name="MY CDH">
      <description>I've just overrridden the default and supplied my own name and description, to show what can be done.</description>
    </template>
  </catalog>
  
  <!-- and a few remote catalogs -->
  <catalog url="http://cloudsoftcorp.com/amp-brooklyn-catalog.xml"/>
  <catalog url="http://microsoot.com/oofice-catalog.xml"/>
    
</catalog>
