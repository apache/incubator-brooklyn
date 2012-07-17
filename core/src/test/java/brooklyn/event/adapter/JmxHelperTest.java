package brooklyn.event.adapter;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

import javax.management.DynamicMBean;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.StandardEmitterMBean;

import org.jclouds.util.Throwables2;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.collections.Lists;

import brooklyn.test.GeneralisedDynamicMBean;
import brooklyn.test.JmxService;
import brooklyn.test.TestUtils;
import brooklyn.util.MutableMap;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class JmxHelperTest {
    
    private static final int TIMEOUT_MS = 5000;
    private static final int SHORT_WAIT_MS = 250;
    
    private JmxService jmxService;
    private JmxHelper jmxHelper;
    
    private String objectName = "Brooklyn:type=MyTestMBean,name=myname";
    private ObjectName jmxObjectName;
    private String attributeName = "myattrib";
    private String opName = "myop";
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        jmxObjectName = new ObjectName(objectName);
        jmxService = new JmxService("localhost", 40123);
        jmxHelper = new JmxHelper(jmxService.getUrl());
        jmxHelper.connect(TIMEOUT_MS);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (jmxHelper != null) jmxHelper.disconnect();
        if (jmxService != null) jmxService.shutdown();
    }

    @Test
    public void testGetAttribute() throws Exception {
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(MutableMap.of("myattr", "myval"), objectName);
        assertEquals(jmxHelper.getAttribute(jmxObjectName, "myattr"), "myval");
    }

    @Test
    public void testSetAttribute() throws Exception {
        DynamicMBean mbean = jmxService.registerMBean(MutableMap.of("myattr", "myval"), objectName);

        jmxHelper.setAttribute(jmxObjectName, "myattr", "abc");
        Object actual = jmxHelper.getAttribute(jmxObjectName, "myattr");
        assertEquals(actual, "abc");
    }

    @Test
    public void testInvokeOperationWithNoArgs() {
        final String opReturnVal = "my result";
        MBeanOperationInfo opInfo = new MBeanOperationInfo(opName, "my descr", new MBeanParameterInfo[0], String.class.getName(), MBeanOperationInfo.ACTION);
        Callable<String> opImpl = new Callable<String>() {
            public String call() {
                return opReturnVal;
            }
        };
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(ImmutableMap.of(), ImmutableMap.of(opInfo, opImpl), objectName);
        
        assertEquals(jmxHelper.operation(objectName, opName), opReturnVal);
    }

    @Test
    public void testInvokeOperationWithArgs() {
        final String opReturnPrefix = "my result prefix/";
        String opParam1 = "my param 1";
        MBeanOperationInfo opInfo = new MBeanOperationInfo(
                opName, 
                "my descr", 
                new MBeanParameterInfo[] {new MBeanParameterInfo("myParam1", String.class.getName(), "my param1 descr")}, 
                String.class.getName(), 
                MBeanOperationInfo.ACTION);
        Function<Object[],String> opImpl = new Function<Object[],String>() {
            public String apply(Object[] input) {
                return opReturnPrefix+input[0];
            }
        };
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(ImmutableMap.of(), ImmutableMap.of(opInfo, opImpl), objectName);
        
        assertEquals(jmxHelper.operation(objectName, opName, opParam1), opReturnPrefix+opParam1);
    }

    @Test
    public void testReconnectsOnJmxServerTemporaryFailure() throws Exception {
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(MutableMap.of("myattr", "myval"), objectName);
        assertEquals(jmxHelper.getAttribute(jmxObjectName, "myattr"), "myval");
        
        // Simulate temporary network-failure
        jmxService.shutdown();

        // Ensure that we have a failed query while the "network is down"         
        try {
            jmxHelper.getAttribute(jmxObjectName, attributeName);
            fail();
        } catch (Exception e) {
            if (Throwables2.getFirstThrowableOfType(e, IOException.class) == null) {
                throw e;
            }
        }
        try {
            jmxHelper.getAttribute(jmxObjectName, attributeName);
            fail();
        } catch (Exception e) {
            if (Throwables2.getFirstThrowableOfType(e, IOException.class) == null) {
                throw e;
            }
        }

        // Simulate the network restarting
        jmxService = new JmxService("localhost", 40123);
        
        GeneralisedDynamicMBean mbean2 = jmxService.registerMBean(MutableMap.of("myattr", "myval2"), objectName);
        assertEquals(jmxHelper.getAttribute(jmxObjectName, "myattr"), "myval2");
    }
    
    @Test(expectedExceptions = {IllegalStateException.class})
    public void testJmxCheckInstanceExistsEventuallyThrowsIfNotFound() throws Exception {
        jmxHelper.assertMBeanExistsEventually(new ObjectName("Brooklyn:type=DoesNotExist,name=doesNotExist"), 1L);
    }

    @Test
    public void testJmxObjectCheckExistsEventuallyReturnsIfFoundImmediately() throws Exception {
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(objectName);
        jmxHelper.assertMBeanExistsEventually(jmxObjectName, 1L);
    }

    @Test
    public void testJmxObjectCheckExistsEventuallyTakingLongReturnsIfFoundImmediately() throws Exception {
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(objectName);
        jmxHelper.assertMBeanExistsEventually(jmxObjectName, 1L);
    }

    @Test
    public void testJmxObjectCheckExistsEventuallyReturnsIfCreatedDuringPolling() throws Exception {
        Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(SHORT_WAIT_MS);
                        GeneralisedDynamicMBean mbean = jmxService.registerMBean(objectName);
                    } catch (InterruptedException e) {
                        return; // graceful return
                    }
                }});
        try {
            t.start();
            
            jmxHelper.assertMBeanExistsEventually(jmxObjectName, TIMEOUT_MS);
        } finally {
            t.interrupt();
            t.join(TIMEOUT_MS);
            assertFalse(t.isAlive());
        }        
    }

    @Test
    public void testSubscribeToJmxNotificationsDirectlyWithJmxHelper() throws Exception {
        StandardEmitterMBean mbean = jmxService.registerMBean(ImmutableList.of("one"), objectName);
        int sequence = 0;
        final List<Notification> received = Lists.newArrayList();

        jmxHelper.addNotificationListener(jmxObjectName, new NotificationListener() {
            public void handleNotification(Notification notif, Object callback) {
                received.add(notif);
            }});
                    

        final Notification notif = sendNotification(mbean, "one", sequence++, "abc");

        TestUtils.executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                assertEquals(received.size(), 1);
                assertNotificationsEqual(received.get(0), notif);
            }});
    }

    // Visual-inspection test that LOG.warn happens only once; TODO setup a listener to the logging output
    @Test
    public void testMBeanNotFoundLoggedOnlyOncePerUrl() throws Exception {
        ObjectName wrongObjectName = new ObjectName("DoesNotExist:type=DoesNotExist");

        // Expect just one log message about:
        //     JMX object DoesNotExist:type=DoesNotExist not found at service:jmx:rmi://localhost:1099/jndi/rmi://localhost:9001/jmxrmi"
        for (int i = 0; i < 10; i++) {
            jmxHelper.findMBean(wrongObjectName);
        }

        jmxService.shutdown();
        jmxHelper.disconnect();
        
        jmxService = new JmxService("127.0.0.1", (int)(11000+(100*Math.random())));
        jmxHelper = new JmxHelper(jmxService.getUrl());
        jmxHelper.connect();
        
        // Expect just one log message about:
        //     JMX object DoesNotExist:type=DoesNotExist not found at service:jmx:rmi://127.0.0.1:1099/jndi/rmi://localhost:9001/jmxrmi"
        for (int i = 0; i < 10; i++) {
            jmxHelper.findMBean(wrongObjectName);
        }
    }

    private Notification sendNotification(StandardEmitterMBean mbean, String type, long seq, Object userData) {
        Notification notif = new Notification(type, mbean, seq);
        notif.setUserData(userData);
        mbean.sendNotification(notif);
        return notif;
    }
    
    private void assertNotificationsEqual(Notification n1, Notification n2) {
        assertEquals(n1.getType(), n2.getType());
        assertEquals(n1.getSequenceNumber(), n2.getSequenceNumber());
        assertEquals(n1.getUserData(), n2.getUserData());
        assertEquals(n1.getTimeStamp(), n2.getTimeStamp());
        assertEquals(n1.getMessage(), n2.getMessage());
    }
}
