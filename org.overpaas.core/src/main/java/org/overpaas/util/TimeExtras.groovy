package org.overpaas.util

import groovy.time.TimeDuration

import java.util.concurrent.TimeUnit

/**
 * Classloading this class will cause multiply/add to be made available on TimeDuration.
 * For example, I could write: 2*TimeUnit.MINUTES+5*TimeUnit.SECONDS.
 * 
 * That is why nothing seems to use this class, because the methods it defines are not 
 * on this class!
 * 
 * @author alex
 */
class TimeExtras {

	static void init() {}
	static {
		Number.metaClass.multiply << { TimeUnit t -> new TimeDuration(t.toMillis(intValue())) }
		Number.metaClass.multiply << { TimeDuration t -> t.multiply(doubleValue()) }
		
		TimeDuration.metaClass.multiply << { Number n -> new TimeDuration( (int)(toMilliseconds()*n) ) }
		TimeDuration.metaClass.constructor << { long millis ->
			def shift = { int modulus -> int v=millis%modulus; millis/=modulus; v }
			def l = [shift(1000), shift(60), shift(60), shift(24), (int)millis]
			Collections.reverse(l)
			l as TimeDuration
		}
	}
}
