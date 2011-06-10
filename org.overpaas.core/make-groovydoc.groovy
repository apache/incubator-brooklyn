def docsDirectory = '.'
def mainSourceDirectory = './src/main/java'
def title = '<a href="http://www.overpaas.org/">OverPaas</a>'
def docFooter = '<a href="http://www.overpaas.org/">OverPaas</a> is sponsored by Cloudsoft Corporation'

def ant = new AntBuilder()
ant.taskdef(name: "groovydoc", classname: "org.codehaus.groovy.ant.Groovydoc")
ant.groovydoc(
              destdir      : "${docsDirectory}/gapi",
              sourcepath   : "${mainSourceDirectory}",
              packagenames : "**.*",
              use          : "true",
              windowtitle  : "${title}",
              doctitle     : "${title}",
              header       : "${title}",
              footer       : "${docFooter}",
              overview     : "src/main/overview.html",
              private      : "false",
              {
                  link(packages:"java.,org.xml.,javax.,org.xml.",
                       href:"http://download.oracle.com/javase/6/docs/api")
                  link(packages:"groovy.,org.codehaus.groovy.",
                       href:"http://groovy.codehaus.org/api")
                  link(packages:"org.junit.,junit.framework.",
                       href:"http://kentbeck.github.com/junit/javadoc/latest")
              }
)
