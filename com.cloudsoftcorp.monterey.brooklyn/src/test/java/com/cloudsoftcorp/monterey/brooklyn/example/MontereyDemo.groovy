package com.cloudsoftcorp.monterey.brooklyn.example

import brooklyn.demo.Demo;
import brooklyn.entity.basic.AbstractApplication;

class MontereyDemo extends Demo {

    public static void main(String[] argv) {
        MontereyDemo demoRunner = new MontereyDemo()
        demoRunner.start(argv)
    }

    @Override
    protected AbstractApplication createApplication() {
        MontereyExampleApp app = new MontereyExampleApp(name:'brooklyn-wide-area-demo',
                displayName:'Brooklyn Wide-Area Spring Travel Demo Application')
        return app
    }
}
