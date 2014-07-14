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
package brooklyn.util.text;

import java.util.Random;

public class Identifiers {
    
    private static Random random = new Random();
    
    public static final String JAVA_GOOD_START_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_";
    public static final String JAVA_GOOD_NONSTART_CHARS = JAVA_GOOD_START_CHARS+"1234567890";
    
    public static final String JAVA_GENERATED_IDENTIFIER_START_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public static final String JAVA_GENERATED_IDENTIFIERNONSTART_CHARS = JAVA_GENERATED_IDENTIFIER_START_CHARS+"1234567890";

    public static final String BASE64_VALID_CHARS = JAVA_GENERATED_IDENTIFIERNONSTART_CHARS+"+=";
    
    public static final String ID_VALID_START_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public static final String ID_VALID_NONSTART_CHARS = ID_VALID_START_CHARS+"1234567890";
    
    /** makes a random id string (letters and numbers) of the given length;
     * starts with letter (upper or lower) so can be used as java-id;
     * tests ensure random distribution, so random ID of length 5 
     * is about 2^29 possibilities 
     * <p>
     * With ID of length 4 it is not unlikely (15% chance) to get
     * duplicates in the first 2000 attempts.
     * With ID of length 8 there is 1% chance to get duplicates
     * in the first 1M attempts and 50% for the first 16M.
     * <p>
     * implementation is efficient, uses char array, and 
     * makes one call to random per 5 chars; makeRandomId(5)
     * takes about 4 times as long as a simple Math.random call,
     * or about 50 times more than a simple x++ instruction;
     * in other words, it's appropriate for contexts where random id's are needed,
     * but use efficiently (ie cache it per object), and 
     * prefer to use a counter where feasible
     * <p>
     * in general this is preferable to base64 as is more portable,
     * can be used throughout javascript (as ID's which don't allow +)
     * or as java identifiers (which don't allow numbers in the first char)
     **/
    public static String makeRandomId(int l) {
        //this version is 30-50% faster than the old double-based one, 
        //which computed a random every 3 turns --
        //takes about 600 ns to do id of len 10, compared to 10000 ns for old version [on 1.6ghz machine]
        if (l<=0) return "";
        char[] id = new char[l];
        int d = random.nextInt( (26+26) * (26+26+10) * (26+26+10) * (26+26+10) * (26+26+10));
        int i = 0;    
        id[i] = ID_VALID_START_CHARS.charAt(d % (26+26));
        d /= (26+26);
        if (++i<l) do {
            id[i] = ID_VALID_NONSTART_CHARS.charAt(d%(26+26+10));
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

    /** creates a short identifier comfortable in java and OS's, given an input hash code
     * <p>
     * result is always at least of length 1, shorter if the hash is smaller */ 
    public static String makeIdFromHash(long d) {
        StringBuffer result = new StringBuffer();
        if (d<0) d=-d;
        // correction for Long.MIN_VALUE
        if (d<0) d=-(d+1000);
        
        result.append(ID_VALID_START_CHARS.charAt((int)(d % (26+26))));
        d /= (26+26);
        while (d!=0) {
            result.append(ID_VALID_NONSTART_CHARS.charAt((int)(d%(26+26+10))));
            d /= (26+26+10);
        }
        return result.toString();
    }
    
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
    public static String makeRandomJavaId(int l) {
            // copied from Monterey util's com.cloudsoftcorp.util.StringUtils.
            // TODO should share code with makeRandomId, just supplying different char sets (though the char sets in fact are the same..)

            //this version is 30-50% faster than the old double-based one, 
            //which computed a random every 3 turns --
            //takes about 600 ns to do id of len 10, compared to 10000 ns for old version [on 1.6ghz machine]
            if (l<=0) return "";
            char[] id = new char[l];
            int d = random.nextInt( (26+26) * (26+26+10) * (26+26+10) * (26+26+10) * (26+26+10));
            int i = 0;    
            id[i] = JAVA_GENERATED_IDENTIFIER_START_CHARS.charAt(d % (26+26));
            d /= (26+26);
            if (++i<l) do {
                    id[i] = JAVA_GENERATED_IDENTIFIERNONSTART_CHARS.charAt(d%(26+26+10));
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

    public static double randomDouble() {
        return random.nextDouble();
    }
    public static long randomLong() {
        return random.nextLong();
    }
    public static boolean randomBoolean() {
        return random.nextBoolean();
    }
    public static int randomInt() {
        return random.nextInt();
    }
    /** returns in [0,upbound) */
    public static int randomInt(int upbound) {
        return random.nextInt(upbound);
    }
    /** returns the array passed in */
    public static byte[] randomBytes(byte[] buf) {
        random.nextBytes(buf);
        return buf;
    }
    public static byte[] randomBytes(int length) {
        byte[] buf = new byte[length];
        return randomBytes(buf);
    }

    public static String makeRandomBase64Id(int length) {
        StringBuilder s = new StringBuilder();
        while (length>0) {
            appendBase64IdFromValueOfLength(randomLong(), length>10 ? 10 : length, s);
            length -= 10;
        }
        return s.toString();
    }
    public static String getBase64IdFromValue(long value) {
        return getBase64IdFromValue(value, 10);
    }
    public static String getBase64IdFromValue(long value, int length) {
        StringBuilder s = new StringBuilder();
        appendBase64IdFromValueOfLength(value, length, s);
        return s.toString();
    }
    public static void appendBase64IdFromValueOfLength(long value, int length, StringBuffer sb) {
        if (length>11)
            throw new IllegalArgumentException("can't get a Base64 string longer than 11 chars from a long");
        long idx = value;
        for (int i=0; i<length; i++) {
            byte x = (byte)(idx & 63);
            sb.append(BASE64_VALID_CHARS.charAt(x));
            idx = idx >> 6;
        }
    }
    public static void appendBase64IdFromValueOfLength(long value, int length, StringBuilder sb) {
        if (length>11)
            throw new IllegalArgumentException("can't get a Base64 string longer than 11 chars from a long");
        long idx = value;
        for (int i=0; i<length; i++) {
            byte x = (byte)(idx & 63);
            sb.append(BASE64_VALID_CHARS.charAt(x));
            idx = idx >> 6;
        }
    }
    
    public static boolean isValidToken(String token, String validStartChars, String validSubsequentChars) {
        if (token==null || token.length()==0) return false;
        if (validStartChars.indexOf(token.charAt(0))==-1) return false;
        for (int i=1; i<token.length(); i++)
            if (validSubsequentChars.indexOf(token.charAt(i))==-1) return false;
        return true;
    }
}
