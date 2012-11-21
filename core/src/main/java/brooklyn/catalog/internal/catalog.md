


<catalog>
  <name>My Local Catalog</name>
  <!-- scan means it will load templates based on @CatalogTemplate annotations on entities;
       recommended only for local elements to prevent having to download JARs just to populate catalog.
       (from a local scan you can generate the XML for publishing by saying  
        `mgmt.getCatalog().toXmlString()` in JS gui groovy page, then tweaking.)  
       also, for a local (non-linked) reference, an empty classpath means to use the default classpath. -->
  <classpath scan="true"/>

  <!-- now (for illustration) we define some other sources which weren't on our classpath but
       which we want included in our catalog on our brooklyn server -->
  <catalog>
    <classpath>
      <entry>file://~/.m2/repository/io/cloudsoft/brooklyn-mapr/1.0.0-SNAPSHOT/brooklyn-mapr.jar</entry>
    </classpath>
    <!-- templates explicitly listed since we didn't scan above (NB scan=false is the default) -->
    <template type="io.brooklyn.mapr.M3App" name="M3 Application"/>
  </catalog>
  
  <catalog>
    <description>Extra local jars I've got on my machine, added so I can pull in the CDH easily.</description>
    <classpath scan="true">
      <entry>file://~/.m2/repository/io/cloudsoft/brooklyn-cdh/1.0.0-SNAPSHOT/brooklyn-cdh.jar</entry>
      <entry>file://~/.m2/repository/io/cloudsoft/brooklyn-cdh/1.0.0-SNAPSHOT/whirr-cm.jar</entry>
    </classpath>
    <!-- templates here were autodetected (scan=true), so we don't _need_ to list any entities;
         but here we illustrate how we can override the name of one of them -->
    <template type="io.brooklyn.cloudera.ClouderaForHadoopWithManager" name="MY FAV!  CDH Hadoop Application with Cloudera Manager">
      <description>I've just overrridden the default and supplied my own name and description, to show what can be done.</description>
    </templaate>
  </catalog>
  
  <!-- and a few remote catalogs -->
  <catalog url="http://cloudsoftcorp.com/amp-brooklyn-catalog.xml"/>
  <catalog url="http://microsoot.com/oofice-catalog.xml"/>
    
</catalog>
