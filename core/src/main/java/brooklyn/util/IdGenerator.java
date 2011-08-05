package brooklyn.util;

import java.util.Random;

/**
 * Select utility methods copied from Monterey util's com.cloudsoftcorp.util.StringUtils.
 */
public class IdGenerator {
    private static Random random = new Random();
    private static String idCharsStart = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static String idCharsSubseq = idCharsStart+"1234567890";

    /** makes a random id string (letters and numbers) of the given length;
     * starts with letter (upper or lower) so can be used as java-id;
     * tests ensure random distribution, so random ID of length 5 
     * is about 2^29 possibilities 
     * <p>
     * implementation is efficient, uses char array, and 
     * makes one call to random per 5 chars; makeRandomId(5)
     * takes about 4 times as long as a simple Math.random call,
     * or about 50 times more than a simple x++ instruction;
     * in other words, it's appropriate for contexts where random id's are needed,
     * but use efficiently (ie cache it per object), and 
     * prefer to use a counter where feasible
     **/
    public static String makeRandomId(int l) {
            //this version is 30-50% faster than the old double-based one, 
            //which computed a random every 3 turns --
            //takes about 600 ns to do id of len 10, compared to 10000 ns for old version [on 1.6ghz machine]
            if (l<=0) return "";
            char[] id = new char[l];
            int d = random.nextInt( (26+26) * (26+26+10) * (26+26+10) * (26+26+10) * (26+26+10));
            int i = 0;    
            id[i] = idCharsStart.charAt(d % (26+26));
            d /= (26+26);
            if (++i<l) do {
                    id[i] = idCharsSubseq.charAt(d%(26+26+10));
                    if (++i>=l) break;
                    if (i%5==0) {
                            d = random.nextInt( (26+26+10) * (26+26+10) * (26+26+10) * (26+26+10) * (26+26+10));
                    } else {
                            d /= (26+26+10);
                    }
            } while (true);
            //Message.message("random id is " + id);
            return new String(id);
    }
    
}
