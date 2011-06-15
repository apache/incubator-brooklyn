/*
 * Copyright (c) 2009-2011 Cloudsoft Corporation Ltd. All rights reserved.
 * Supplied under license http://www.cloudsoftcorp.com/license/montereyDeveloperEdition
 * or such subsequent license agreed between Cloudsoft Corporation Ltd and the licensee.
 */
package com.cloudsoftcorp.monterey.brooklyn.entity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Properties;

import org.apache.commons.codec.binary.Base64;

import com.cloudsoftcorp.util.exception.ExceptionUtils;
import com.cloudsoftcorp.util.io.StreamUtils;
import com.cloudsoftcorp.util.web.server.WebServer;

/**
 * Parent class for various cloud account configurations.
 * 
 * TODO add JMX properties
 *
 * @author adk
 */
public class MontereyNetworkConfig implements Serializable {
    /** serialVersionUID */
    private static final long serialVersionUID = 5181096081594953360L;
    
    public static final String PROVIDER_PROPERTY = "PROVIDER";
    public static final String IDENTITY_PROPERTY = "IDENTITY";
    public static final String CREDENTIAL_PROPERTY = "CREDENTIAL";
    public static final String MONTEREY_WEB_API_PORT_PROPERTY = "MONTEREY_WEB_API_PORT";
    public static final String MONTEREY_WEB_API_PROTOCOL_PROPERTY = "MONTEREY_WEB_API_PROTOCOL";
    public static final String MONTEREY_WEB_API_SSL_KEYSTORE_PROPERTY = "MONTEREY_WEB_API_SSL_KEYSTORE";
    public static final String MONTEREY_WEB_API_SSL_KEYSTORE_DATA_PROPERTY = "MONTEREY_WEB_API_SSL_KEYSTORE_DATA";
    public static final String MONTEREY_WEB_API_SSL_KEYSTORE_PASSWORD_PROPERTY = "MONTEREY_WEB_API_SSL_KEYSTORE_PASSWORD";
    public static final String MONTEREY_WEB_API_SSL_KEY_PASSWORD_PROPERTY = "MONTEREY_WEB_API_SSL_KEY_PASSWORD";
    public static final String MONTEREY_NODE_PORT_PROPERTY = "MONTEREY_NODE_PORT";
    public static final String MONTEREY_HUB_LPP_PORT_PROPERTY = "MONTEREY_HUB_LPP_PORT";
    public static final String INSTANCE_TYPE_PROPERTY = "INSTANCE_TYPE";
    public static final String MONTEREY_IMAGE_NAME_PROPERTY = "IMAGE_NAME";
    public static final String MONTEREY_IMAGE_VERSION_PROPERTY = "MONTEREY_IMAGE_VERSION";
    public static final String SSH_PORT_PROPERTY = "SSH_PORT";
    public static final String SECURITY_GROUP_PROPERTY = "SECURITY_GROUP";
    public static final String LOGGING_FILE_OVERRIDE_PROPERTY = "LOGGING_FILE_OVERRIDE";
	public static final String CUSTOM_LOCATIONS_PROPERTY = "CUSTOM_LOCATIONS";

    public static final int SSH_PORT = 22;
    public static final int MONTEREY_NODE_PORT = 43500;
    public static final int MONTEREY_HUB_LPP_PORT = 43501;

    public static final String SSH_KEY_FILE_PROPERTY_PREFIX = "SSH_KEY_FILE.";
    public static final String SSH_KEY_PROPERTY_PREFIX = "SSH_KEY.";
    public static final String SSH_PRIVATE_KEY_FILE_PROPERTY = "SSH_PRIVATE_KEY_FILE";
    public static final String SSH_PUBLIC_KEY_FILE_PROPERTY = "SSH_PUBLIC_KEY_FILE";
    public static final String SSH_PRIVATE_KEY_PROPERTY = "SSH_PRIVATE_KEY";
    public static final String SSH_PUBLIC_KEY_PROPERTY = "SSH_PUBLIC_KEY";

    public static final String USERNAME = "root";
    public static final String MONTEREY_HOME = "/home/monterey";

    // TODO standardise on one representation that works everywhere
    public static final String MONTEREY_MANAGER_HOME = MONTEREY_HOME+"/monterey-management-node";
    public static final String MONTEREY_NETWORK_HOME = MONTEREY_HOME+"/monterey-network-node";
    public static final String DEFAULT_NETWORK_HOME = "~/monterey-network-node";
    public static final String DEFAULT_MANAGER_HOME = "~/monterey-manager-node";

    public static final String MANAGER_SIDE_START_SCRIPT_RELATIVE_PATH = "scripts/osgi-management-node-start.sh";
    public static final String MANAGER_SIDE_KILL_SCRIPT_RELATIVE_PATH = "scripts/management-node-kill.sh";
    public static final String MANAGER_SIDE_LOGGING_FILE_OVERRIDE_RELATIVE_PATH = "conf/customized-network-node-logging.properties";
    public static final String MANAGER_SIDE_LOGGING_FILE_RELATIVE_PATH = "conf/logging.properties";
    public static final String MANAGER_SIDE_WEBUSERS_FILE_RELATIVE_PATH = "conf/web-users.conf";
    public static final String MANAGER_SIDE_MANAGEMENT_CONF_FILE_RELATIVE_PATH = "conf/management.conf";
    public static final String MANAGER_SIDE_WEB_CONF_FILE_RELATIVE_PATH = "conf/web.conf";
    public static final String MANAGER_SIDE_JMX_CONF_FILE_RELATIVE_PATH = "conf/jmx.conf";
    public static final String MANAGER_SIDE_LOG_REMOTE_LAUNCH_RELATIVE_PATH = "log/remote-launch.log";
    public static final String MANAGER_SIDE_SSL_KEYSTORE_RELATIVE_PATH = "conf/keystore";
    
    public static final String MANAGER_SIDE_START_SCRIPT_ABSOLUTE_PATH = MONTEREY_MANAGER_HOME+"/"+MANAGER_SIDE_START_SCRIPT_RELATIVE_PATH;
    public static final String MANAGER_SIDE_LOGGING_FILE_OVERRIDE_ABSOLUTE_PATH = MONTEREY_MANAGER_HOME+"/"+MANAGER_SIDE_LOGGING_FILE_OVERRIDE_RELATIVE_PATH;
    public static final String MANAGER_SIDE_LOGGING_FILE_ABSOLUTE_PATH = MONTEREY_MANAGER_HOME+"/"+MANAGER_SIDE_LOGGING_FILE_RELATIVE_PATH;
    public static final String MANAGER_SIDE_WEBUSERS_FILE_ABSOLUTE_PATH = MONTEREY_MANAGER_HOME+"/"+MANAGER_SIDE_WEBUSERS_FILE_RELATIVE_PATH;
    public static final String MANAGER_SIDE_LOG_REMOTE_LAUNCH_ABSOLUTE_PATH = MONTEREY_MANAGER_HOME+"/"+MANAGER_SIDE_LOG_REMOTE_LAUNCH_RELATIVE_PATH;
    public static final String MANAGER_SIDE_SSL_KEYSTORE_ABSOLUTE_PATH = MONTEREY_MANAGER_HOME+"/"+MANAGER_SIDE_SSL_KEYSTORE_RELATIVE_PATH;
    public static final String MANAGER_SIDE_WEB_CONF_FILE_ABSOLUTE_PATH = MONTEREY_MANAGER_HOME+"/"+MANAGER_SIDE_WEB_CONF_FILE_RELATIVE_PATH;

    public static final String NETWORK_NODE_SIDE_LOGGING_FILE_RELATIVE_PATH = "conf/logging.properties";
    public static final String NETWORK_NODE_START_SCRIPT_RELATIVE_PATH = "scripts/osgi-network-node-start.sh";
    public static final String NETWORK_NODE_KILL_SCRIPT_RELATIVE_PATH = "scripts/network-node-kill.sh";
    public static final String NETWORK_NODE_SSL_TRUSTSTORE_RELATIVE_PATH = "conf/truststore";
	
    public static final String NETWORK_NODE_SIDE_LOGGING_FILE_ABSOLUTE_PATH = MONTEREY_NETWORK_HOME+"/"+NETWORK_NODE_SIDE_LOGGING_FILE_RELATIVE_PATH;
    public static final String NETWORK_NODE_START_SCRIPT_ABSOLUTE_PATH = MONTEREY_NETWORK_HOME+"/"+NETWORK_NODE_START_SCRIPT_RELATIVE_PATH;
    public static final String NETWORK_NODE_KILL_SCRIPT_ABSOLUTE_PATH = MONTEREY_NETWORK_HOME+"/"+NETWORK_NODE_KILL_SCRIPT_RELATIVE_PATH;

    public static final int TIMEOUT_FOR_NEW_NETWORK_ON_HOST = 60*1000;
    
    private int montereyNodePort;
    private int montereyHubLppPort;
	private int sshPort;
    
	public int getDefaultMontereyNodePort() { return MONTEREY_NODE_PORT; }
	public int getDefaultMontereyHubLppPort() { return MONTEREY_HUB_LPP_PORT; }
	public int getDefaultSSHPort() { return SSH_PORT; }
	
    protected int montereyWebApiPort;
	protected String montereyWebApiProtocol;
	protected File montereyWebApiSslKeystore = null;
	protected String montereyWebApiSslKeystoreData = null;
	protected String montereyWebApiSslKeystorePassword = null;
	protected String montereyWebApiSslKeyPassword = null;

    protected File loggingFileOverride;
    
    public MontereyNetworkConfig() { /* for gson; and for default :-( */
        montereyNodePort = getDefaultMontereyNodePort();
        montereyHubLppPort = getDefaultMontereyHubLppPort();
        montereyWebApiPort = WebServer.DEFAULT_HTTP_PORT;
        montereyWebApiProtocol = WebServer.HTTP;
        sshPort = getDefaultSSHPort();
    }

    public MontereyNetworkConfig(Properties props) {
    	montereyNodePort = getIntegerProperty(props, MONTEREY_NODE_PORT_PROPERTY, getDefaultMontereyNodePort());
    	montereyHubLppPort = getIntegerProperty(props, MONTEREY_HUB_LPP_PORT_PROPERTY, getDefaultMontereyHubLppPort());
    	montereyWebApiPort = getIntegerProperty(props, MONTEREY_WEB_API_PORT_PROPERTY, WebServer.DEFAULT_HTTP_PORT);
    	montereyWebApiProtocol = props.getProperty(MONTEREY_WEB_API_PROTOCOL_PROPERTY, WebServer.HTTP);
    	montereyWebApiSslKeystore = getFileProperty(props, MONTEREY_WEB_API_SSL_KEYSTORE_PROPERTY);
    	montereyWebApiSslKeystoreData = props.getProperty(MONTEREY_WEB_API_SSL_KEYSTORE_DATA_PROPERTY);
        montereyWebApiSslKeystorePassword = props.getProperty(MONTEREY_WEB_API_SSL_KEYSTORE_PASSWORD_PROPERTY);
        montereyWebApiSslKeyPassword = props.getProperty(MONTEREY_WEB_API_SSL_KEY_PASSWORD_PROPERTY);
        sshPort = getIntegerProperty(props, SSH_PORT_PROPERTY, getDefaultSSHPort());
        loggingFileOverride = getFileProperty(props, LOGGING_FILE_OVERRIDE_PROPERTY);
    }
    
    public Properties toProperties() {
        Properties result = new Properties();
 
        result.setProperty(MONTEREY_NODE_PORT_PROPERTY, Integer.toString(montereyNodePort));
        result.setProperty(MONTEREY_HUB_LPP_PORT_PROPERTY, Integer.toString(montereyHubLppPort));
        result.setProperty(MONTEREY_WEB_API_PORT_PROPERTY, Integer.toString(montereyWebApiPort));
        result.setProperty(MONTEREY_WEB_API_PROTOCOL_PROPERTY, montereyWebApiProtocol);

        if (montereyWebApiSslKeystore != null) result.setProperty(MONTEREY_WEB_API_SSL_KEYSTORE_PROPERTY, montereyWebApiSslKeystore.getPath());
        if (montereyWebApiSslKeystorePassword != null) result.setProperty(MONTEREY_WEB_API_SSL_KEYSTORE_PASSWORD_PROPERTY, montereyWebApiSslKeystorePassword);
        if (montereyWebApiSslKeyPassword != null) result.setProperty(MONTEREY_WEB_API_SSL_KEY_PASSWORD_PROPERTY, montereyWebApiSslKeyPassword);
 
        result.setProperty(SSH_PORT_PROPERTY, Integer.toString(sshPort));
        if (loggingFileOverride != null) result.setProperty(LOGGING_FILE_OVERRIDE_PROPERTY, loggingFileOverride.getPath());
        
        return result;
    }
    
    public Properties generateResourceProvisionerConf() {
        Properties result = new Properties();
        
        result.setProperty(MONTEREY_NODE_PORT_PROPERTY, Integer.toString(montereyNodePort));
        result.setProperty(MONTEREY_HUB_LPP_PORT_PROPERTY, Integer.toString(montereyHubLppPort));
        result.setProperty(MONTEREY_WEB_API_PORT_PROPERTY, Integer.toString(montereyWebApiPort));
        result.setProperty(MONTEREY_WEB_API_PROTOCOL_PROPERTY, montereyWebApiProtocol);

        if (montereyWebApiSslKeystore != null && montereyWebApiSslKeystore.exists()) {
            byte[] data;
            try {
                data = StreamUtils.getBytesFromStream(new FileInputStream(montereyWebApiSslKeystore));
            } catch (IOException e) {
                throw ExceptionUtils.throwRuntime("Exception while opening "+montereyWebApiSslKeystore, e);
            }
            result.setProperty(MONTEREY_WEB_API_SSL_KEYSTORE_DATA_PROPERTY, new String(Base64.encodeBase64(data)));
            result.setProperty(MONTEREY_WEB_API_SSL_KEYSTORE_PROPERTY, montereyWebApiSslKeystore.getPath());
        }
        if (montereyWebApiSslKeystorePassword != null) result.setProperty(MONTEREY_WEB_API_SSL_KEYSTORE_PASSWORD_PROPERTY, montereyWebApiSslKeystorePassword);
        if (montereyWebApiSslKeyPassword != null) result.setProperty(MONTEREY_WEB_API_SSL_KEY_PASSWORD_PROPERTY, montereyWebApiSslKeystorePassword);

        result.setProperty(SSH_PORT_PROPERTY, Integer.toString(sshPort));
        if (loggingFileOverride != null && loggingFileOverride.exists())
        	result.setProperty(LOGGING_FILE_OVERRIDE_PROPERTY, loggingFileOverride.getPath());
 
        return result;
    }
    
    public static File getFileProperty(Properties props, String property) {
        if (props.getProperty(property) != null && props.getProperty(property).trim().length() > 0)
            return new File(props.getProperty(property));
        else return null;
    }
    
    public static int getIntegerProperty(Properties props, String property, int defaultValue) {
        return Integer.parseInt(props.getProperty(property, Integer.toString(defaultValue)));
    }

    public int getMontereyHubLppPort() { return montereyHubLppPort; }
	public void setMontereyHubLppPort(int montereyHubLppPort) { this.montereyHubLppPort = montereyHubLppPort; }

	public int getMontereyNodePort() { return montereyNodePort; }
	public void setMontereyNodePort(int montereyNodePort) { this.montereyNodePort = montereyNodePort; }

	public int getMontereyWebApiPort() { return montereyWebApiPort; }
	public void setMontereyWebApiPort(int montereyWebApiPort) { this.montereyWebApiPort = montereyWebApiPort; }

    public String getMontereyWebApiProtocol() { return montereyWebApiProtocol; }
	public void setMontereyWebApiProtocol(String montereyWebApiProtocol) { this.montereyWebApiProtocol = montereyWebApiProtocol; }

    public File getMontereyWebApiSslKeystore() { return montereyWebApiSslKeystore; }
	public void setMontereyWebApiSslKeystore(File montereyWebApiSslKeystore) { this.montereyWebApiSslKeystore = montereyWebApiSslKeystore; }

    public String getMontereyWebApiSslKeystoreData() { return montereyWebApiSslKeystoreData; }
	public void setMontereyWebApiSslKeystoreData(String montereyWebApiSslKeystoreData) { this.montereyWebApiSslKeystoreData = montereyWebApiSslKeystoreData; }

    public String getMontereyWebApiSslKeystorePassword() { return montereyWebApiSslKeystorePassword; }
	public void setMontereyWebApiSslKeystorePassword( String montereyWebApiSslKeystorePassword) { this.montereyWebApiSslKeystorePassword = montereyWebApiSslKeystorePassword; }

    public String getMontereyWebApiSslKeyPassword() { return montereyWebApiSslKeyPassword; }
	public void setMontereyWebApiSslKeyPassword(String montereyWebApiSslKeyPassword) { this.montereyWebApiSslKeyPassword = montereyWebApiSslKeyPassword; }

	public int getSshPort() { return sshPort; }
	public void setSshPort(int sshPort) { this.sshPort = sshPort; }

    public File getLoggingFileOverride() { return loggingFileOverride; }
	public void setLoggingFileOverride(File loggingFileOverride) { this.loggingFileOverride = loggingFileOverride; }
}

