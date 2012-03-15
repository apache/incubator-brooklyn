import grails.util.Environment;

grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
//grails.project.war.file = "target/${appName}-${appVersion}.war"
grails.project.dependency.resolution = {
    // inherit Grails' default dependencies
    inherits("global") {
        // disable ehcache (we don't use it); and log4j (we are embedded so have our own logger, usually;
        // with this as is it seems to delegate to our preferred logger (of container), so hurrah)
        excludes 'ehcache' 

        //have experimented excluding the following but it causes more problem that it's worth... sticking with log4j for now
        // 'grails-plugin-logging', 'log4j', 'junit', 'slf4j-api', 'slf4j-log4j12' 'jcl-over-slf4j', 'jul-to-slf4j'
        //this has no effect; we exclude in the pom instead, that keeps the war a bit smaller
	//excludes 'groovy-all', 'org.codehaus.groovy'
                     
        // following exclusions also recommended to resolve some sax / xml nasties
        // (which you may see if you add brooklyn-extra below and try grails run-app) ... but don't work 
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
//        test 'brooklyn:brooklyn-core:'+appVersion
        test 'brooklyn:brooklyn-software-webapp:'+appVersion
        
        //instead of transitive:false the following might work better:
//        excludes "org.codehaus.groovy","org.jclouds"
    }

    // seems to do nothing? despite much documentation to the contrary
//    defaultDependenciesProvided true
}

//this makes the war much leaner but is lacking org.springframework.web.filter.DelegatingFilterProxy,
//and if we put that in the container it breaks when it tries to instantiate a g:render tag
//solution is probably to make the war skinny as a post-processing step, and have ALL jars in the container
/*
if (Environment.current==Environment.DEVELOPMENT) {
    // in "dev" environment build a small WAR (set by maven depending on build params)
    grails.war.dependencies = {
        fileset(dir: "${grailsSettings.baseDir}/lib") { }
    }
}
*/
