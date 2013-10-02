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

public class ImageChoosers {

    private static final Logger log = LoggerFactory.getLogger(ImageChoosers.class);
    
    protected static int compare(double left, double right) {
        double delta = left - right;
        return delta < 0.000000001 ? -1 : delta > 0.00000001 ? 1 : 0;
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
    
    public static double punishmentForOldOsVersions(Image img, OsFamily family, double minVersion) {
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
    
    public static List<String> BLACKLISTED_IMAGE_IDS = Arrays.asList(
                // bad natty image - causes 403 on attempts to apt-get; https://bugs.launchpad.net/ubuntu/+bug/987182
                "us-east-1/ami-1cb30875"
            );

    public static List<String> WHITELISTED_IMAGE_IDS = Arrays.asList(
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
    
    public static final Ordering<Image> GOOD_AND_BAD_IMAGES_ORDERING = new Ordering<Image>() {
        public double score(Image img) {
            double score = 0;

            if (BLACKLISTED_IMAGE_IDS.contains(img.getId()))
                score -= 50;

            if (WHITELISTED_IMAGE_IDS.contains(img.getId()))
                // NB: this should be less than deprecated punishment to catch deprecation of whitelisted items
                score += 20;

            if (imageNameContainsWordCaseInsensitive(img, "deprecated")) score -= 30;
            if (imageNameContainsWordCaseInsensitive(img, "alpha")) score -= 10;
            if (imageNameContainsWordCaseInsensitive(img, "beta")) score -= 5;
            if (imageNameContainsWordCaseInsensitive(img, "testing")) score -= 5;
            if (imageNameContainsWordCaseInsensitive(img, "rc")) score -= 3;

            // prefer these guys, in stock brooklyn provisioning
            score += punishmentForOldOsVersions(img, OsFamily.UBUNTU, 11);
            score += punishmentForOldOsVersions(img, OsFamily.CENTOS, 6);
            
            OperatingSystem os = img.getOperatingSystem();
            if (os!=null && os.getFamily()!=null) {
                // preference for these open, popular OS (but only wrt versions above) 
                if (os.getFamily().equals(OsFamily.CENTOS)) score += 2;
                else if (os.getFamily().equals(OsFamily.UBUNTU)) score += 2;
                
                // slight preference for these 
                else if (os.getFamily().equals(OsFamily.RHEL)) score += 1;
                else if (os.getFamily().equals(OsFamily.AMZN_LINUX)) score += 1;
                else if (os.getFamily().equals(OsFamily.DEBIAN)) score += 1;
                
                // prefer to take our chances with unknown / unlabelled linux than something explicitly windows
                else if (os.getFamily().equals(OsFamily.WINDOWS)) score -= 1;
            }
            
            // TODO prefer known providerIds

            if (log.isTraceEnabled())
                log.trace("Score "+score+" for "+img);
            
            return score;
        }

        @Override
        public int compare(Image left, Image right) {
            return ImageChoosers.compare(score(left), score(right));
        }
    };

    public static final Ordering<Image> BROOKLYN_DEFAULT_IMAGES_ORDERING = new Ordering<Image>() {
        @Override
        public int compare(Image left, Image right) {
            return ComparisonChain.start()
                    .compare(left, right, GOOD_AND_BAD_IMAGES_ORDERING)
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
    
    public static final Function<Iterable<? extends Image>,Image> BROOKLYN_DEFAULT_IMAGE_CHOOSER = imageChooserFromOrdering(BROOKLYN_DEFAULT_IMAGES_ORDERING);
    
}
