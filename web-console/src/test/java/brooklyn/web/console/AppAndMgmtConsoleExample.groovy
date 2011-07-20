package brooklyn.web.console

import brooklyn.management.internal.LocalManagementContext;

class AppAndMgmtConsoleExample {

//    
//    // 1:
//    public static void main(String[] args) {
//        def mgmtCtx = new LocalManagementContext();
//        //or:
//        //new ProxyRemoteManagementContext(URL);
//        
////        def warRunner = new Jetty("console.war", [mgmt: mgmtCtx, port:80, sslPort:443, ...]);
//        //or
////        BroolynWeb.startManagementConsole(mgmtCtx);
//        
//        def myApp = new MyApp(mgmt: mgmtCtx);
//        myApp.start();
//    }
//    
//    static class MyApp extends Application {
//        //...
//    }
    
}

/*
 * another way:
 * 
 * deploy war to standard container
 * 
 * connect gui/rest to configure mgmt plane details (or become mgmt plane)
 * 
 * use gui/rest to deploy an application, by supplying jar and classname, or groovy script (and jars)
 */
