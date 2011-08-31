package brooklyn.location.basic.jclouds

import java.util.Map

import org.jclouds.Constants

class JcloudsLocationFactory {

    // FIXME streetAddress is temporary, until we get lat-lon working in google maps properly
    
	private static final Map AWS_EC2_DEFAULT_IMAGE_IDS = [
		"eu-west-1":"eu-west-1/ami-89def4fd",
		"us-east-1":"us-east-1/ami-2342a94a",
		"us-west-1":"us-west-1/ami-25df8e60",
		"ap-southeast-1":"ap-southeast-1/ami-21c2bd73",
		"ap-northeast-1":"ap-northeast-1/ami-f0e842f1",
		]

    private static final Map locationSpecificConf = [
            "gogrid":[
                    "1":[
                            providerLocationId:"1",
                            displayName:"GoGrid us-west",
                            streetAddress:"California",
                            'latitude' : 40.0d, 'longitude' : -120.0d,
                            iso3166:["US-CA"]], // Northern California (approx)
                        ],
            "aws-ec2":[
                    "us-west-1":[
        					providerLocationId:"us-west-1",
        					displayName:"AWS us-west",
        					streetAddress:"California",
        					'latitude' : 40.0d, 'longitude' : -120.0d,
        					iso3166:["US-CA"],
        					defaultImageId : AWS_EC2_DEFAULT_IMAGE_IDS.get("us-west-1") ], // Northern California (approx)
        				"us-east-1":[
        					providerLocationId:"us-east-1",
        					displayName:"AWS us-east",
        					streetAddress:"Virginia",
        					'latitude' : 38.0d, 'longitude' : -76.0d,
        					iso3166:["US-VA"],
        					defaultImageId : AWS_EC2_DEFAULT_IMAGE_IDS.get("us-east-1") ], // Northern Virginia (approx)
        				"eu-west-1":[
        					providerLocationId:"eu-west-1",
        					displayName:"AWS eu-west",
        					streetAddress:"Dublin, Ireland",
        					'latitude' : 53.34778d, 'longitude' : -6.25972d,
        					iso3166:["IE"],
        					defaultImageId : AWS_EC2_DEFAULT_IMAGE_IDS.get("eu-west-1") ], // Dublin, Ireland
        				"ap-southeast-1":[
        					providerLocationId:"ap-southeast-1",
        					displayName:"AWS ap-southeast",
        					streetAddress:"Singapore",
        					'latitude' : 0d, 'longitude' : 0d,
        					iso3166:["SG"],
        					defaultImageId : AWS_EC2_DEFAULT_IMAGE_IDS.get("ap-southeast-1") ],
        				"ap-northeast-1":[
        					providerLocationId:"ap-northeast-1",
        					streetAddress:"Tokyo, Japan",
        					displayName:"AWS ap-northeast",
        					'latitude' : 0d, 'longitude' : 0d,
        					iso3166:["JP"],
        					defaultImageId : AWS_EC2_DEFAULT_IMAGE_IDS.get("ap-northeast-1") ]
						]
		]

    private final Map conf = [:]
    
    public JcloudsLocationFactory(Map conf) {
        this.conf = [:]
        this.conf << conf
    }
    
    public JcloudsLocationFactory(String identity, String credential) {
        this([identity:identity, credential:credential])
    }

    public JcloudsLocation newLocation(String provider, String locationId) {
        Map locSpecifics = locationSpecificConf.get(provider)?.get(locationId)
        Map allconf = [:]
        allconf << conf
        allconf.provider = provider
        allconf.providerLocationId = locationId
        allconf << (locSpecifics ?: [:])
        return new JcloudsLocation(allconf);
    }
}
