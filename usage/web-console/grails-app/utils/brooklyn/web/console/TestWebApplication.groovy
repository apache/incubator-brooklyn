package brooklyn.web.console

import grails.converters.JSON
import groovy.time.TimeDuration;

import java.util.concurrent.TimeUnit

import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.ParameterType
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.BasicParameterType
import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.Location
import brooklyn.location.basic.SimulatedLocation
import brooklyn.management.Task
import brooklyn.policy.Policy
import brooklyn.policy.basic.GeneralPurposePolicy
import brooklyn.util.internal.TimeExtras;
import brooklyn.util.task.ScheduledTask
import brooklyn.web.console.entity.TestEffector

// TODO remove these test classes as soon as the group agrees they're unnecessary!
private class TestWebApplication extends AbstractApplication {
    TestWebApplication(Map props=[:]) {
        super(props)
        displayName = "Application";

        locations = [
            new SimulatedLocation([id: "us-east-1", name:"US-East-1", iso3166: "US-CA", displayName:"US-East-1", streetAddress:"Northern Virginia, USA", description:"Northern Virginia (approx)",
                                        latitude:38.0,longitude:-76.0]),
            new SimulatedLocation([id: "us-west-1", name:"US-West-1", iso3166: "US-VA", displayName:"US-West-1", streetAddress:"Northern California, USA", description:"Northern California",
                                        latitude:40.0,longitude:-120.0]),
            new SimulatedLocation([id: "eu-west-1", name:"EU-West-1", iso3166: "IE", displayName:"EU-West-1", streetAddress:"Dublin, Ireland", description:"Dublin, Ireland",
                                        latitude:53.34778,longitude:-6.25972]),
            new SimulatedLocation([id: "fruitcake", name:"Unused location in cakeland", iso3166: "IE", displayName:"Unused location in cakeland", streetAddress:"Nowhere, cakeland", description:"Nowhere",
                                        latitude:0,longitude:0])
        ];

        List<Policy> testPolicies = [
            new GeneralPurposePolicy([id: 'CTS1', name: 'chase-the-sun', displayName: 'Chase the Sun', policyStatus: 'Suspended']),
            new GeneralPurposePolicy([id: 'CTM1', name: 'chase-the-moon', displayName: 'Chase the Moon', policyStatus: 'Active']),
            new GeneralPurposePolicy([id: 'FTM1', name: 'follow-the-money', displayName: 'Follow the Money', policyStatus: 'Suspended']),
            new GeneralPurposePolicy([id: 'FTA1', name: 'follow-the-action', displayName: 'Follow the Action', policyStatus: 'Active'])
        ];


        Entity testExtraGroup = new TestGroupEntity(this, "Another group for testing");
        setupChangingEntity(this);
        
        for(String tierName : ["tomcat tier 1", "tomcat tier 2", "data tier 1"]) {
            Entity tier = new TestGroupEntity(this, tierName);
            for(String clusterName : ["1a", "1b"]) {
                Entity cluster = new TestGroupEntity(tier, tierName.substring(0, tierName.indexOf(" ")) +
                        " cluster " + clusterName)
                for(int i=1; i<4; i++) {
                    if (tierName =~ /^tomcat/) {
                        Entity testTomcat = new TestTomcatEntity(cluster, "tomcat node " + clusterName + "." + i)
                        testTomcat.addGroup(testExtraGroup);
                        cluster.addOwnedChild(testTomcat)
                        setUpAddingSensor(testTomcat)
                    } else {
                        cluster.addOwnedChild(new TestDataEntity(cluster, "data node " + clusterName + "." + i))
                    }

                }
                tier.addOwnedChild(cluster)
            }
            addOwnedChild(tier)
        }

        sensors.putAll([
                Children: new BasicAttributeSensor<Integer>(Integer.class, "Children",
                        "Owned children of this application"),
                        DataRate: new BasicAttributeSensor<String>(String.class, "DataRate")])
        setAttribute(getSensor("Children"), getOwnedChildren().size())
    }

    public <T> Task<T> invoke(Effector<T> eff, Map<String, ?> parameters) {
        return null  //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * Sets up an entity that is added and removed every 20s.
     * @param application
     */
    private void setupChangingEntity(final AbstractApplication application) {
        Runnable r = new Runnable() {
            Entity e;
            void run() {
                while (true) {
                    if (e != null) {
                        application.removeOwnedChild(e)
                        e = null;
                        Thread.sleep(20*1000L);
                    } else {
                        e = new TestGroupEntity(application, "Now you see me");
                        Thread.sleep(20*1000L);
                    }
                }
            }

        };

        new Thread(r).start();
    }

    private void setUpAddingSensor(AbstractEntity entity) {
        Runnable r = new Runnable() {
            void run() {
                while (true) {
                    Sensor sensor = new BasicAttributeSensor(Sensor.class, "test.sensor", "Added and removed every 20s")
                    entity.addSensor(sensor)
                    Thread.sleep(20*1000L)
                    entity.removeSensor(sensor.name)
                    Thread.sleep(20*1000L)
                }
            }

        };
        new Thread(r).start();
    }

    private class TestGroupEntity extends AbstractGroup {
        TestGroupEntity(Entity owner, String displayName) {
            super([:], owner)
            this.displayName = displayName
            sensors.putAll([Children: new BasicAttributeSensor<Integer>(Integer.class, "Children",
                    "Direct children of this group"), DataRate: new BasicAttributeSensor<String>(String.class, "DataRate")])
        }

        TestGroupEntity addOwnedChild(Entity child) {
            super.addOwnedChild(child)
            setAttribute(getSensor("Children"), ownedChildren.size())
            return this
        }

        public <T> Task<T> invoke(Effector<T> eff, Map<String, ?> parameters) {
            return null
        }
    }


    private class TestDataEntity extends AbstractEntity {
        private List<Location> testLocations = [
            new SimulatedLocation([id: "us-east-1", name:"US-East-1", iso3166: "US-CA", displayName:"US-East-1", streetAddress:"Northern Virginia, USA", description:"Northern Virginia (approx)",
                                        latitude:38.0,longitude:-76.0]),
            new SimulatedLocation([id: "us-west-1", name:"US-West-1", iso3166: "US-VA", displayName:"US-West-1", streetAddress:"Northern California, USA", description:"Northern California",
                                        latitude:40.0,longitude:-120.0]),
            new SimulatedLocation([id: "eu-west-1", name:"EU-West-1", iso3166: "IE", displayName:"EU-West-1", streetAddress:"Dublin, Ireland", description:"Dublin, Ireland",
                                        latitude:53.34778,longitude:-6.25972])
        ];
        private List<Policy> testPolicies = [
            new GeneralPurposePolicy([id: 'CTS1', name: 'chase-the-sun', displayName: 'Chase the Sun', policyStatus: 'Suspended', description: 'Chasing the sun, meaning chase the activity when certain parts of the earth are awake']),
            new GeneralPurposePolicy([id: 'CTM1', name: 'chase-the-moon', displayName: 'Chase the Moon', policyStatus: 'Active'])
        ];
        TestDataEntity(Entity owner, String displayName) {
            super([:], owner)

            this.displayName = displayName
            this.locations = testLocations;
            this.policies = testPolicies;
            TestEffector startDB = new TestEffector("Start DB", "This will start the database",
                    new ArrayList<ParameterType<?>>())
            TestEffector stopDB = new TestEffector("Stop DB", "This will stop the database",
                    new ArrayList<ParameterType<?>>())
            TestEffector restartDB = new TestEffector("Restart DB", "This will restart the DB",
                    new ArrayList<ParameterType<?>>())

            this.effectors.putAll(["Start DB": startDB, "Stop DB": stopDB, "Restart DB": restartDB])

            this.sensors.putAll(
                   [Happiness: new BasicAttributeSensor<String>(String.class, "Happiness"),
                    Cache: new BasicAttributeSensor<String>(String.class, "Cache", "Some cache metric"),
                    Sync: new BasicAttributeSensor<String>(String.class, "Sync", "Synchronization strategy")]
            )

            setAttribute(getSensor("Happiness"), 50)
            setAttribute(getSensor("Cache"), 200)
            setAttribute(getSensor("Sync"), "Moop")
        }

        public <T> Task<T> invoke(Effector<T> eff, Map<String, ?> parameters) {
            return null
        }
    }

    private class TestTomcatEntity extends AbstractEntity {
        //FIXME should use typed keys not strings
        private Map hackMeIn = [
                "http.port": 8080,
                "webapp.tomcat.shutdownPort": 666,
                "jmx.port": 1000,
                "webapp.reqs.processing.time": 100,
                "test.sensor": 17
        ]

        private List<Location> testLocations = [
                new SimulatedLocation([id: "us-east-1", name:"US-East-1", iso3166: "US-VA", displayName:"US-East-1", streetAddress:"Northern Virginia, USA", description:"Northern Virginia (approx)",
                                            latitude:38.0,longitude:-76.0])
        ];
        private List<Policy> testPolicies = [
            new GeneralPurposePolicy([id: 'FTM1', name: 'follow-the-money', displayName: 'Follow the Money', policyStatus: 'Suspended']),
            new GeneralPurposePolicy([id: 'FTA1', name: 'follow-the-action', displayName: 'Follow the Action', policyStatus: 'Active'])
        ];


        public TestTomcatEntity(Entity owner, String displayName) {
            super([:], owner)
            this.displayName = displayName
            this.locations = testLocations;
            this.policies = testPolicies;

            // Stealing the sensors from TomcatNode
            this.sensors.putAll(new TomcatServer().sensors)

            List<ParameterType<?>> parameterTypeList = new ArrayList<ParameterType<?>>()
            ParameterType tomcatStartLocation = new BasicParameterType("Location", new ArrayList<String>().class)
            ParameterType actionDate = new BasicParameterType("Date", Date.class)
            parameterTypeList.add(tomcatStartLocation)
            parameterTypeList.add(actionDate)


            // Don't appear to be any effectors in TomcatServer
            TestEffector startTomcat = new TestEffector("Start Tomcat",
                                                        "This will start Tomcat at a specified location",
                                                        parameterTypeList)
            TestEffector stopTomcat = new TestEffector("Stop Tomcat",
                                                        "This will stop tomcat at its current location",
                                                        new Collections.SingletonList(actionDate))
            TestEffector restartTomcat = new TestEffector("Restart Tomcat",
                                                          "This will restart tomcat in its current location",
                                                          new ArrayList<ParameterType<?>>())

            this.effectors.putAll([  "Start Tomcat": startTomcat,
                                "Stop Tomcat": stopTomcat,
                                "Restart Tomcat": restartTomcat])

            //updates sensors (this doesn't seem to be working?)
            this.getExecutionContext().submit(
                new ScheduledTask(period: TimeExtras.duration(5, TimeUnit.SECONDS),
                    tags:["EFFECTOR"],
                    tag:this,
                    displayName: "Update values",
                    description: "This updates sensor values",
                    { updateSensorsWithRandoms(TestTomcatEntity.this); }));
                
                updateSensorsWithRandoms(this);
        }

        public <T> Task<T> invoke(Effector<T> eff, Map<String, ?> parameters) {
            System.out.println(parameters as JSON)
            return null
        }

        protected class MyRunnable implements Runnable {
            EntityLocal entity
            protected MyRunnable(Entity e) {
                this.entity = e
            }
            void run() {
                while (true) {
                    updateSensorsWithRandoms(entity);
                    Thread.sleep(5000);
                }
            }
        }
    }
    
    public void updateSensorsWithRandoms(EntityLocal entity) {
        Map ss = entity.getSensors()
        for (String key: entity.hackMeIn.keySet()) {
            def s = ss[key]
//                        System.out.println("updating $entity $ss $s");
            if (s != null){
                entity.setAttribute(s,
                    entity.hackMeIn[key] + ManagementContextService.ID_GENERATOR +
                        ((int) 1000 * Math.random()))
            }
        }
    }
}
