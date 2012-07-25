package brooklyn.location.basic.jclouds;

import brooklyn.util.MutableMap;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class JcloudsLocationFactory {

    // TODO streetAddress is temporary, until we get lat-lon working in google maps properly

    public static final Map AWS_EC2_DEFAULT_IMAGE_IDS = MutableMap.of(
            "eu-west-1", "eu-west-1/ami-89def4fd",
            "us-east-1", "us-east-1/ami-2342a94a",
            "us-west-1", "us-west-1/ami-25df8e60",
            "ap-southeast-1", "ap-southeast-1/ami-21c2bd73",
            "ap-northeast-1", "ap-northeast-1/ami-f0e842f1"
    );

    // Northern California (approx))
    private final static Map<String, Object> GoGrid_us_west = MutableMap.of(
            "providerLocationId", "1",
            "displayName", "GoGrid us-west",
            "streetAddress", "California",
            "latitude", 40.0d,
            "longitude", -120.0d,
            "iso3166", Arrays.asList("US-CA"));

    // Northern California (approx)
    private final static Map<String, Object> aws_ec2_us_west_1 = MutableMap.of(
            "providerLocationId", "us-west-1",
            "displayName", "AWS us-west",
            "streetAddress", "California",
            "latitude", 40.0d, "longitude", -120.0d,
            "iso3166", Arrays.asList("US-CA"),
            "defaultImageId", AWS_EC2_DEFAULT_IMAGE_IDS.get("us-west-1")
    );

    // Northern Virginia (approx)
    private final static Map<String, Object> aws_ec2_us_east_1 = MutableMap.of(
            "providerLocationId", "us-east-1",
            "displayName", "AWS us-east",
            "streetAddress", "Virginia",
            "latitude", 38.0d, "longitude", -76.0d,
            "iso3166", Arrays.asList("US-VA"),
            "defaultImageId", AWS_EC2_DEFAULT_IMAGE_IDS.get("us-east-1")
    );

    // Dublin, Ireland
    private final static Map<String, Object> aws_ec2_eu_west_1 = MutableMap.of(
            "providerLocationId", "eu-west-1",
            "displayName", "AWS eu-west",
            "streetAddress", "Dublin, Ireland",
            "latitude", 53.34778d, "longitude", -6.25972d,
            "iso3166", Arrays.asList("IE"),
            "defaultImageId", AWS_EC2_DEFAULT_IMAGE_IDS.get("eu-west-1")
    );

    private final static Map<String, Object> aws_ec2_ap_southeast_1 = MutableMap.of(
            "providerLocationId", "ap-southeast-1",
            "displayName", "AWS ap-southeast",
            "streetAddress", "Singapore",
            "latitude", 0d, "longitude", 0d,
            "iso3166", Arrays.asList("SG"),
            "defaultImageId", AWS_EC2_DEFAULT_IMAGE_IDS.get("ap-southeast-1")
    );

    private final static Map<String, Object> aws_ec2_ap_northeast_1 = MutableMap.of(
            "providerLocationId", "ap-northeast-1",
            "streetAddress", "Tokyo, Japan",
            "displayName", "AWS ap-northeast",
            "latitude", 0d, "longitude", 0d,
            "iso3166", Arrays.asList("JP"),
            "defaultImageId", AWS_EC2_DEFAULT_IMAGE_IDS.get("ap-northeast-1")
    );

    //TODO:
    //SHould this (and all the other static maps) not be something configurable? So using some kind of property file
    //that is changeable from the outside without needing to modify the code.
    public static final Map locationSpecificConf = MutableMap.of(
            "gogrid", MutableMap.of("1", GoGrid_us_west),
            "aws-ec2",
            MutableMap.of(
                    "us-west-1", aws_ec2_us_west_1,
                    "us-east-1", aws_ec2_us_east_1,
                    "eu-west-1", aws_ec2_eu_west_1,
                    "ap-southeast-1", aws_ec2_ap_southeast_1,
                    "ap-northeast-1", aws_ec2_ap_northeast_1
            )
    );

    private final Map conf;

    public JcloudsLocationFactory(Map conf) {
        this.conf = new LinkedHashMap();
        this.conf.putAll(conf);
    }

    public JcloudsLocationFactory(String provider, String identity, String credential) {
        this(MutableMap.of("provider", provider, "identity", identity, "credential", credential));
    }

    public JcloudsLocation newLocation(String locationId) {
        // load cloud-specific details from above
        // jclouds image auto-detection isn't 100% so we've picked good known default images
        // (we should also use the lat/long specified because MaxMind and others are spotty wrt AWS)
        Map locSpecifics = new LinkedHashMap();
        if (locationId != null) {
            Map l1 = (Map) locationSpecificConf.get(conf.get("provider"));
            if (l1 != null) {
                Map l2 = (Map) l1.get(locationId);
                if (l2 == null) {
                    // look for keys that _start_ with the locationId
                    for (Map.Entry entry : (Set<Map.Entry>) locationSpecificConf.entrySet()) {
                        String key = (String) entry.getKey();
                        if (locationId.startsWith(key)) {
                            l2 = (Map) entry.getValue();
                        }
                    }
                }
                if (l2 != null) {
                    locSpecifics.putAll(l2);
                }
            }
        }
        Map allconf = new LinkedHashMap();
        allconf.putAll(conf);
        if (locationId != null)
            allconf.put("providerLocationId", locationId);
        allconf.putAll(locSpecifics);
        return new JcloudsLocation(allconf);
    }
}
