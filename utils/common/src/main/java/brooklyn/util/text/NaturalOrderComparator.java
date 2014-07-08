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

/*
NaturalOrderComparator.java -- Perform 'natural order' comparisons of strings in Java.
Copyright (C) 2003 by Pierre-Luc Paour <natorder@paour.com>

Based on the C version by Martin Pool, of which this is more or less a straight conversion.
Copyright (C) 2000 by Martin Pool <mbp@humbug.org.au>

Edited for minor java 1.5 isms, -- tef

This software is provided 'as-is', without any express or implied
warranty.  In no event will the authors be held liable for any damages
arising from the use of this software.

Permission is granted to anyone to use this software for any purpose,
including commercial applications, and to alter it and redistribute it
freely, subject to the following restrictions:

1. The origin of this software must not be misrepresented; you must not
claim that you wrote the original software. If you use this software
in a product, an acknowledgment in the product documentation would be
appreciated but is not required.
2. Altered source versions must be plainly marked as such, and must not be
misrepresented as being the original software.
3. This notice may not be removed or altered from any source distribution.
*/

import java.util.Comparator;

/** comparator which takes two strings and puts them in an order with special rules for numbers to be placed in numeric order;
 * e.g. "10">"9", including when those numbers occur in the midst of equal text; e.g. "a10" > "a9";
 * but not if the text differs; e.g. "a10" < "b9"
 * <p>
 * class is thread-safe  
 */
public class NaturalOrderComparator implements Comparator<String> {
    
    public static final NaturalOrderComparator INSTANCE = new NaturalOrderComparator();
    
	int compareRight(String a, String b)
	{
		int bias = 0;
		int ia = 0;
		int ib = 0;

		// The longest run of digits wins.  That aside, the greatest
		// value wins, but we can't know that it will until we've scanned
		// both numbers to know that they have the same magnitude, so we
		// remember it in BIAS.
		for (;; ia++, ib++) {
			char ca = charAt(a, ia);
			char cb = charAt(b, ib);

			if (!Character.isDigit(ca)
					&& !Character.isDigit(cb)) {
				return bias;
			} else if (!Character.isDigit(ca)) {
				return -1;
			} else if (!Character.isDigit(cb)) {
				return +1;
			} else if (ca < cb) {
				if (bias == 0) {
					bias = -1;
				}
			} else if (ca > cb) {
				if (bias == 0)
					bias = +1;
			} else if (ca == 0 && cb == 0) {
				return bias;
			}
		}
	}

	public int compare(String a, String b) {

		int ia = 0, ib = 0;
		int nza = 0, nzb = 0;
		char ca, cb;
		int result;

		while (true) {
			// only count the number of zeroes leading the last number compared
			nza = nzb = 0;

			ca = charAt(a, ia); cb = charAt(b, ib);

			// skip over leading spaces or zeros
			while (Character.isSpaceChar(ca) || ca == '0') {
				if (ca == '0') {
					nza++;
				} else {
					// only count consecutive zeroes
					nza = 0;
				}

				ca = charAt(a, ++ia);
			}

			while (Character.isSpaceChar(cb) || cb == '0') {
				if (cb == '0') {
					nzb++;
				} else {
					// only count consecutive zeroes
					nzb = 0;
				}

				cb = charAt(b, ++ib);
			}

			// process run of digits
			if (Character.isDigit(ca) && Character.isDigit(cb)) {
				if ((result = compareRight(a.substring(ia), b.substring(ib))) != 0) {
					return result;
				}
			}

			if (ca == 0 && cb == 0) {
				// The strings compare the same.  Perhaps the caller
				// will want to call strcmp to break the tie.
				return nza - nzb;
			}

			if (ca < cb) {
				return -1;
			} else if (ca > cb) {
				return +1;
			}

			++ia; ++ib;
		}
	}

	static char charAt(String s, int i) {
		if (i >= s.length()) {
			return 0;
		} else {
			return s.charAt(i);
		}
	}

}