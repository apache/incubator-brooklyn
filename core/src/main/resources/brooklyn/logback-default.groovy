
// if you want to run the groovy config, use this file
// we use XML however, as it can (more easily) be included

appender("STDOUT", ConsoleAppender) {
  filter(ThresholdFilter) {
      level = INFO
  }
  encoder(PatternLayoutEncoder) {
    pattern = "%d %-5level %msg%n"
  }
}

appender("MAIN_FILE", FileAppender) {
    file = "brooklyn.log"
    append = true
    encoder(PatternLayoutEncoder) {
      pattern = "%d %-5level %logger{30} [%thread{15}]: %msg%n"
    }
//log4j.appender.R.layout.ConversionPattern=%d %5p %c: %m%n
//log4j.appender.R.MaxFileSize=100MB
//log4j.appender.R.MaxBackupIndex=10
}

root(INFO, ["STDOUT", "MAIN_FILE"])

// these generate lots of uninteresting info
(expand("org.hibernate.{SQL,type,tool.hbm2ddl}")+
    "org.eclipse.jetty"
).each { logger(it, WARN) }
// schmizz sshj almost always uninteresting; restrict to file
logger("net.schmizz", WARN, ["MAIN_FILE"], false)
// this one can be noisy too; file only
logger("org.apache.whirr.service.ComputeCache", DEBUG, ["MAIN_FILE"], false)

// now go for DEBUG on the root of relevant packages
logger("brooklyn", DEBUG)
logger("jclouds", DEBUG)
logger("org.jclouds", DEBUG)
logger("org.apache.whirr", DEBUG)

// these individual categories are interesting, if you've bumped the above to info
// you might consider re-enabling these
logger("brooklyn.SSH", DEBUG)  // SSH i/o is very useful
logger("brooklyn.location.basic.jclouds", DEBUG)  // jclouds provisioning debug info also useful
logger("brooklyn.util.internal.ssh", DEBUG)
