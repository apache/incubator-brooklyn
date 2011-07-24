package brooklyn.location.basic.aws

import java.util.Map

import org.jclouds.Constants

class AwsLocationFactory {

    private static final Map locationSpecificConf = [
            "us-west-1":[ providerLocationId:"us-west-1", 'latitude' : 40.0, 'longitude' : -120.0 ], // Northern California (approx)
            "us-east-1":[ providerLocationId:"us-east-1", 'latitude' : 38.0, 'longitude' : -76.0 ], // Northern Virginia (approx)
            "eu-west-1":[ providerLocationId:"eu-west-1", 'latitude' : 53.34778, 'longitude' : -6.25972 ], // Dublin, Ireland
            "ap-southeast-1":[ providerLocationId:"ap-southeast-1", 'latitude' : 0, 'longitude' : 0 ],
            "ap-northeast-1":[ providerLocationId:"ap-northeast-1", 'latitude' : 0, 'longitude' : 0 ]
            ]
    
    private final Map conf

    public AwsLocationFactory(Map conf) {
        this.conf = [:]
        this.conf << conf
        this.conf.provider = "aws-ec2"
    }
    
    public AwsLocationFactory(String identity, String credential) {
        this([identity:identity, credential:credential])
    }

    public AwsLocation newLocation(String locationId) {
        if (!locationSpecificConf.containsKey(locationId)) {
            throw new IllegalArgumentException("Unknown location $locationId");
        }
        Map allconf = [:]
        allconf << conf
        allconf << locationSpecificConf.get(locationId)
        allconf.put(Constants.PROPERTY_ENDPOINT, "https://ec2.${locationId}.amazonaws.com/");
        return new AwsLocation(allconf);
    }
}
