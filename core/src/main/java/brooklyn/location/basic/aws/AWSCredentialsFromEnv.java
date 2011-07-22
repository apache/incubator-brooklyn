/*
 * Copyright (c) 2009-2011 Cloudsoft Corporation Ltd. All rights reserved.
 * Supplied under license http://www.cloudsoftcorp.com/license/montereyDeveloperEdition
 * or such subsequent license agreed between Cloudsoft Corporation Ltd and the licensee.
 */
package brooklyn.location.basic.aws;


/**
 * The AWSCredenialsFromEnv
 *
 * @author aled
 **/
public class AWSCredentialsFromEnv {

    public String getAWSAccessKeyId() {
        return returnValueOrThrowException("AWS_ACCESS_KEY_ID");
    }

    public String getAWSSecretKey() {
        return returnValueOrThrowException("AWS_SECRET_ACCESS_KEY");
    }
    
    private String returnValueOrThrowException(String envProp) {
        String value =  System.getenv(envProp);
        if (value == null)
            throw new IllegalStateException("Environment variable "+envProp+" not set");
        return value;
    }               
}

