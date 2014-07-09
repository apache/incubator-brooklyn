package brooklyn.util.jmx.jmxrmi;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

@SuppressWarnings({"rawtypes","unchecked"})
public class JmxRmiClient {

    public void connect(String urlString, Map env) throws MalformedURLException, IOException {
        JMXServiceURL url = new JMXServiceURL(urlString);
        System.out.println("JmxmpClient connecting to "+url);
        JMXConnector jmxc = JMXConnectorFactory.connect(url, env); 
        
        MBeanServerConnection mbsc = jmxc.getMBeanServerConnection(); 
        String domains[] = mbsc.getDomains(); 
        for (int i = 0; i < domains.length; i++) { 
            System.out.println("Domain[" + i + "] = " + domains[i]); 
        } 

        jmxc.close();
    } 

}
