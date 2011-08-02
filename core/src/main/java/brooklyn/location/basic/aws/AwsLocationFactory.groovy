package brooklyn.location.basic.aws

import java.util.Map

import org.jclouds.Constants

class AwsLocationFactory {

    // FIXME streetAddress is temporary, until we get lat-lon working in google maps properly
    
    private static final Map EC2_DEFAULT_IMAGE_IDS = [
            "eu-west-1":"ami-89def4fd",
            "us-east-1":"ami-2342a94a",
            "us-west-1":"ami-25df8e60",
            "ap-southeast-1":"ami-21c2bd73",
            "ap-northeast-1":"ami-f0e842f1",
            ]

    private static final Map locationSpecificConf = [
            "us-west-1":[ 
                providerLocationId:"us-west-1",
                displayName:"AWS us-west", 
                streetAddress:"California",
                'latitude' : 40.0d, 'longitude' : -120.0d, 
                iso3166:"US-CA",
                defaultImageId : EC2_DEFAULT_IMAGE_IDS.get("us-west-1") ], // Northern California (approx)
            "us-east-1":[ 
                providerLocationId:"us-east-1", 
                displayName:"AWS us-east", 
                streetAddress:"Virginia",
                'latitude' : 38.0d, 'longitude' : -76.0d, 
                iso3166:"US-VA",
                defaultImageId : EC2_DEFAULT_IMAGE_IDS.get("us-east-1") ], // Northern Virginia (approx)
            "eu-west-1":[ 
                providerLocationId:"eu-west-1", 
                displayName:"AWS eu-west", 
                streetAddress:"Dublin, Ireland",
                'latitude' : 53.34778d, 'longitude' : -6.25972d, 
                iso3166:"IE",
                defaultImageId : EC2_DEFAULT_IMAGE_IDS.get("eu-west-1") ], // Dublin, Ireland
            "ap-southeast-1":[ 
                providerLocationId:"ap-southeast-1", 
                displayName:"AWS ap-southeast", 
                streetAddress:"Singapore",
                'latitude' : 0d, 'longitude' : 0d, 
                iso3166:"SG",
                defaultImageId : EC2_DEFAULT_IMAGE_IDS.get("ap-southeast-1") ],
            "ap-northeast-1":[ 
                providerLocationId:"ap-northeast-1", 
                streetAddress:"Tokyo, Japan",
                displayName:"AWS ap-northeast", 
                'latitude' : 0d, 'longitude' : 0d, 
                iso3166:"JP",
                defaultImageId : EC2_DEFAULT_IMAGE_IDS.get("ap-northeast-1") ]
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
