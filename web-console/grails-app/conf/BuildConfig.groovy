grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
//grails.project.war.file = "target/${appName}-${appVersion}.war"
grails.project.dependency.resolution = {
    // inherit Grails' default dependencies
    inherits("global") {
        // disable ehcache (we don't use it); and log4j (we are embedded so have our own logger, usually)
        excludes 'ehcache', "grails-plugin-logging", "log4j"
        // following exclusions also recommended to resolve some sax / xml nasties
        // (which you'll see if you add brooklyn-extra below and try grails run-app) ... but don't work 
        // "xml-apis", "xmlParserAPIs", "xalan"
    }
    log "warn" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'
    repositories {
        grailsPlugins()
        grailsHome()
        grailsCentral()

        mavenLocal()
        mavenCentral()   //needed for ivy, etc
        mavenRepo "http://snapshots.repository.codehaus.org"
        mavenRepo "http://repository.codehaus.org"
        mavenRepo "http://download.java.net/maven/2/"
        mavenRepo "http://repository.jboss.com/maven2/"
    }
    dependencies {
        // specify dependencies here under either 'build', 'compile', 'runtime', 'test' or 'provided' scopes eg.
        compile 'brooklyn:brooklyn-api:0.2.0-SNAPSHOT', transitive:false 
        compile 'brooklyn:brooklyn-core:0.2.0-SNAPSHOT', transitive:false
        compile 'brooklyn:brooklyn-extra:0.2.0-SNAPSHOT', transitive:false
        
        //instead of transitive:false the following might work better:
//        excludes "org.codehaus.groovy","org.jclouds"
        
        
    }
}
