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
package brooklyn.util.net;

import java.util.Collection;

/**
 * Given several strings, determines which have the longest, and shorted, initial matching prefix.
 * Particularly useful as a poor-man's way to determine which IP's are likely to be the same subnet.
 */
public class NetworkMultiAddressUtils {

    // TODO should convert to byte arrays then binary, and look at binary digit match length !
    
//  public static Collection<String> sortByInitialSimilarity(final String pattern, Collection<String> targets) {
//      List<String> result = new ArrayList<String>(targets);
//      Collections.sort(result, new Comparator<String>() {
//          @Override
//          public int compare(String o1, String o2) {
//              int i1 = 0; int i2 = 0;
//              for (int i=0; i<pattern.length(); i++) {
//                  if (o1.length()<i || o2.length()<i) break;
//                  if (o1.substring(0, i).equals(anObject))
//              }
//          }
//      });
//      return result;
//  }
  
  public static String getClosest(final String pattern, Collection<String> targets) {
      int score = -1;
      String best = null;
      for (String target: targets) {
          int thisScore = matchLength(pattern, target);
          if (thisScore > score) {
              score = thisScore;
              best = target;
          }
      }
      return best;
  }

  public static String getFurthest(final String pattern, Collection<String> targets) {
      int score = 65535;
      String best = null;
      for (String target: targets) {
          int thisScore = matchLength(pattern, target);
          if (thisScore < score) {
              score = thisScore;
              best = target;
          }
      }
      return best;
  }

  private static int matchLength(String pattern, String target) {
      int i=0;
      while (i<pattern.length() && i<target.length() && pattern.charAt(i)==target.charAt(i))
          i++;
      return i;
  }

}
