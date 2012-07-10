package brooklyn.web.console

import grails.converters.JSON

import java.util.concurrent.TimeUnit

import com.google.common.collect.ImmutableList

import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.ParameterType
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.BasicParameterType
import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.event.Sensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.Location
import brooklyn.location.basic.SimulatedLocation
import brooklyn.management.Task
import brooklyn.policy.Policy
import brooklyn.policy.basic.GeneralPurposePolicy
import brooklyn.util.BrooklynLanguageExtensions;
import brooklyn.util.internal.TimeExtras;
import brooklyn.util.task.ScheduledTask
import brooklyn.web.console.entity.TestEffector

// TODO remove these test classes as soon as the group agrees they're unnecessary!
public class TestWebApplication extends AbstractApplication {
    
    static { BrooklynLanguageExtensions.reinit() }

    public static final BasicAttributeSensor<Integer> CHILDREN = new BasicAttributeSensor<Integer>(Integer.class, "Children",
                "Owned children of this application");
    public static final BasicAttributeSensor<String> DATA_RATE = new BasicAttributeSensor<String>(String.class, "DataRate");

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

        setAttribute(CHILDREN, getOwnedChildren().size())
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

    public static class TestGroupEntity extends AbstractGroup {
        public static final BasicAttributeSensor<Integer> CHILDREN = new BasicAttributeSensor<Integer>(Integer.class, "Children",
            "Direct children of this group");
        public static final BasicAttributeSensor<String> DATA_RATE = new BasicAttributeSensor<String>(String.class, "DataRate");

        TestGroupEntity(Entity owner, String displayName) {
            super([:], owner)
            this.displayName = displayName
        }

        public Entity addOwnedChild(Entity child) {
            // TODO using super.addOwnedChild gives StackOverflowException. Sounds similar to http://jira.codehaus.org/browse/GROOVY-5385,
            // except that changing the return type to match super's doesn't fix it...
            child.setOwner(this)
            setAttribute(CHILDREN, ownedChildren.size())
            return this
        }

        public <T> Task<T> invoke(Effector<T> eff, Map<String, ?> parameters) {
            return null
        }
    }


    public static class TestDataEntity extends AbstractEntity {
        public static final BasicAttributeSensor<String> HAPPINESS = new BasicAttributeSensor<String>(String.class, "Happiness");
        public static final BasicAttributeSensor<String> CACHE = new BasicAttributeSensor<String>(String.class, "Cache", "Some cache metric");
        public static final BasicAttributeSensor<String> SYNC = new BasicAttributeSensor<String>(String.class, "Sync", "Synchronization strategy");

        public static final TestEffector START_DB = new TestEffector("Start DB", "This will start the database",
                new ArrayList<ParameterType<?>>());
        public static final TestEffector STOP_DB = new TestEffector("Stop DB", "This will stop the database",
                new ArrayList<ParameterType<?>>());
        public static final TestEffector RESTART_DB = new TestEffector("Restart DB", "This will restart the DB",
                new ArrayList<ParameterType<?>>());
            
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

            setAttribute(HAPPINESS, 50)
            setAttribute(CACHE, 200)
            setAttribute(SYNC, "Moop")
        }

        public <T> Task<T> invoke(Effector<T> eff, Map<String, ?> parameters) {
            return null
        }
    }

    public static class TestTomcatEntity extends AbstractEntity {
        public static final TestEffector START_TOMCAT = new TestEffector("Start Tomcat",
                "This will start Tomcat at a specified location",
                ImmutableList.of(new BasicParameterType("Location", new ArrayList<String>().class), new BasicParameterType("Date", Date.class)));
        public static final TestEffector STOP_TOMCAT = new TestEffector("Stop Tomcat",
                "This will stop tomcat at its current location",
                new Collections.SingletonList(new BasicParameterType("Date", Date.class)));
        public static final TestEffector RESTART_TOMCAT = new TestEffector("Restart Tomcat",
                "This will restart tomcat in its current location",
                new ArrayList<ParameterType<?>>());

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
            this.getMutableEntityType().addSensors(new TomcatServer().getEntityType().getSensors());

            //updates sensors (this doesn't seem to be working?)
            TestTomcatEntity tc = this;  //NB: ref to TestTomcatEntity.this breaks mvn build
            this.getExecutionContext().submit(
                new ScheduledTask(period: TimeExtras.duration(5, TimeUnit.SECONDS),
                    tags:["EFFECTOR"],
                    tag:this,
                    displayName: "Update values",
                    description: "This updates sensor values",
                    { updateSensorsWithRandoms(tc); }));
                
            updateSensorsWithRandoms(this);
            setAttribute(TomcatServer.ROOT_URL, "http://localhost:8080/my-web-app-here");
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
    
    public static void updateSensorsWithRandoms(EntityLocal entity) {
        for (String key: entity.hackMeIn.keySet()) {
            Sensor s = entity.getEntityType().getSensor(key)
            if (s != null){
                entity.setAttribute(s,
                    entity.hackMeIn[key] + ManagementContextService.ID_GENERATOR +
                        ((int) 1000 * Math.random()))
            }
        }
    }
}
