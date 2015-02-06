/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.location.jclouds;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.OperatingSystem;
import org.jclouds.compute.domain.OsFamily;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.collections.MutableList;

import com.google.common.base.Function;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import com.google.common.math.DoubleMath;

public class BrooklynImageChooser {

    private static final Logger log = LoggerFactory.getLogger(BrooklynImageChooser.class);
    
    protected static int compare(double left, double right) {
        return DoubleMath.fuzzyCompare(left, right, 0.00000001);
    }
    
    protected static boolean imageNameContains(Image img, String pattern) {
        if (img.getName()==null) return false;
        return img.getName().contains(pattern);
    }
    
    protected static boolean imageNameContainsCaseInsensitive(Image img, String pattern) {
        if (img.getName()==null) return false;
        return img.getName().toLowerCase().contains(pattern.toLowerCase());
    }
    
    protected static boolean imageNameContainsWordCaseInsensitive(Image img, String pattern) {
        if (img.getName()==null) return false;
        return img.getName().toLowerCase().matches("(.*[^a-z])?"+pattern.toLowerCase()+"([^a-z].*)?");
    }
    
    public double punishmentForOldOsVersions(Image img, OsFamily family, double minVersion) {
        OperatingSystem os = img.getOperatingSystem();
        if (os!=null && family.equals(os.getFamily())) {
            String v = os.getVersion();
            if (v!=null) {
                try {
                    double vd = Double.parseDouble(v);
                    // punish older versions, with a -log function (so 0.5 version behind is -log(1.5)=-0.5 and 2 versions behind is -log(3)=-1.2  
                    if (vd < minVersion) return -Math.log(1+(minVersion - vd));
                } catch (Exception e) {
                    /* ignore unparseable versions */
                }
            }
        }
        return 0;
    }
    
    public List<String> blackListedImageIds() {
        return Arrays.asList(
                // bad natty image - causes 403 on attempts to apt-get; https://bugs.launchpad.net/ubuntu/+bug/987182
                "us-east-1/ami-1cb30875"
            );
    }

    public List<String> whilelistedImageIds() {
        return Arrays.asList(
        // these are the ones we recommend in brooklyn.properties, but now autodetection should be more reliable
//                "us-east-1/ami-d0f89fb9",
//                "us-west-1/ami-fe002cbb",
//                "us-west-2/ami-70f96e40",
//                "eu-west-1/ami-ce7b6fba",
//                "sa-east-1/ami-a3da00be",
//                "ap-southeast-1/ami-64084736",
//                "ap-southeast-2/ami-04ea7a3e",
//                "ap-northeast-1/ami-fe6ceeff"
            );
    }
    
    public double score(Image img) {
        double score = 0;

        if (blackListedImageIds().contains(img.getId()))
            score -= 50;

        if (whilelistedImageIds().contains(img.getId()))
            // NB: this should be less than deprecated punishment to catch deprecation of whitelisted items
            score += 20;

        score += punishmentForDeprecation(img);

    
        // prefer these guys, in stock brooklyn provisioning
        score += punishmentForOldOsVersions(img, OsFamily.UBUNTU, 11);
        score += punishmentForOldOsVersions(img, OsFamily.CENTOS, 6);

        OperatingSystem os = img.getOperatingSystem();
        if (os!=null) {
            if (os.getFamily()!=null) {
                // preference for these open, popular OS (but only wrt versions above) 
                if (os.getFamily().equals(OsFamily.CENTOS)) score += 2;
                else if (os.getFamily().equals(OsFamily.UBUNTU)) {
                    score += 2;

                    // prefer these LTS releases slightly above others (including above CentOS)
                    // (but note in AWS Virginia, at least, version is empty for the 14.04 images for some reason, as of Aug 2014)
                    if ("14.04".equals(os.getVersion())) score += 0.2;
                    else if ("12.04".equals(os.getVersion())) score += 0.1;

                    // NB some 13.10 images take 20m+ before they are sshable on AWS
                    // with "vesafb: module verification error" showing in the AWS system log
                }

                // slight preference for these 
                else if (os.getFamily().equals(OsFamily.RHEL)) score += 1;
                else if (os.getFamily().equals(OsFamily.AMZN_LINUX)) score += 1;
                else if (os.getFamily().equals(OsFamily.DEBIAN)) score += 1;

                // prefer to take our chances with unknown / unlabelled linux than something explicitly windows
                else if (os.getFamily().equals(OsFamily.WINDOWS)) score -= 1;
            }
            // prefer 64-bit
            if (os.is64Bit()) score += 0.5;
        }

        // TODO prefer known providerIds

        if (log.isTraceEnabled())
            log.trace("initial score "+score+" for "+img);
        
        return score;
    }

    protected double punishmentForDeprecation(Image img) {
        // google deprecation strategy
        //        userMetadata={deprecatedState=DEPRECATED}}
        String deprecated = img.getUserMetadata().get("deprecatedState");
        if (deprecated!=null) {
            if ("deprecated".equalsIgnoreCase(deprecated))
                return -30;
            log.warn("Unrecognised 'deprecatedState' value '"+deprecated+"' when scoring "+img+"; ignoring that metadata");
        }
        
        // common strategies
        if (imageNameContainsWordCaseInsensitive(img, "deprecated")) return -30;
        if (imageNameContainsWordCaseInsensitive(img, "alpha")) return -10;
        if (imageNameContainsWordCaseInsensitive(img, "beta")) return -5;
        if (imageNameContainsWordCaseInsensitive(img, "testing")) return -5;
        if (imageNameContainsWordCaseInsensitive(img, "rc")) return -3;

        // no indication this is deprecated
        return 0;
    }

    public Ordering<Image> orderingScoredWithoutDefaults() {
        return new Ordering<Image>() {
            @Override
            public int compare(Image left, Image right) {
                return BrooklynImageChooser.compare(score(left), score(right));
            }
        };
    }
    
    public static Ordering<Image> orderingWithDefaults(final Ordering<Image> primaryOrdering) {
        return new Ordering<Image>() {
            @Override
            public int compare(Image left, Image right) {
                return ComparisonChain.start()
                    .compare(left, right, primaryOrdering)
                    // fall back to default strategy otherwise, except preferring *non*-null values
                    // TODO use AlphaNum string comparator
                    .compare(left.getName(), right.getName(), Ordering.<String> natural().nullsFirst())
                    .compare(left.getVersion(), right.getVersion(), Ordering.<String> natural().nullsFirst())
                    .compare(left.getDescription(), right.getDescription(), Ordering.<String> natural().nullsFirst())
                    .compare(left.getOperatingSystem().getName(), right.getOperatingSystem().getName(), Ordering.<String> natural().nullsFirst())
                    .compare(left.getOperatingSystem().getVersion(), right.getOperatingSystem().getVersion(), Ordering.<String> natural().nullsFirst())
                    .compare(left.getOperatingSystem().getDescription(), right.getOperatingSystem().getDescription(), Ordering.<String> natural().nullsFirst())
                    .compare(left.getOperatingSystem().getArch(), right.getOperatingSystem().getArch(), Ordering.<String> natural().nullsFirst()).result();
            }
        };
    }
    
    public static Function<Iterable<? extends Image>, Image> imageChooserFromOrdering(final Ordering<Image> ordering) {
        return new Function<Iterable<? extends Image>, Image>() {
            @Override
            public Image apply(Iterable<? extends Image> input) {
                List<? extends Image> maxImages = multiMax(ordering, input);
                return maxImages.get(maxImages.size() - 1);
            }
        };
    }

    // from jclouds
    static <T, E extends T> List<E> multiMax(Comparator<T> ordering, Iterable<E> iterable) {
        Iterator<E> iterator = iterable.iterator();
        List<E> maxes = MutableList.of(iterator.next());
        E maxSoFar = maxes.get(0);
        while (iterator.hasNext()) {
           E current = iterator.next();
           int comparison = ordering.compare(maxSoFar, current);
           if (comparison == 0) {
              maxes.add(current);
           } else if (comparison < 0) {
              maxes = MutableList.of(current);
              maxSoFar = current;
           }
        }
        return maxes;
     }
    
    public Ordering<Image> ordering() {
        return orderingWithDefaults(orderingScoredWithoutDefaults());
    }

    public Function<Iterable<? extends Image>,Image> chooser() {
        return imageChooserFromOrdering(ordering());
    }
    
}
