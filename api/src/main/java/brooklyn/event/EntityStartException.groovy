package brooklyn.event

import groovy.transform.InheritConstructors

/**
 * Indicate an exception when attempting to start an entity.
 * @author richardcloudsoft; Richard Downer <richard.downer@cloudsoftcorp.com>
 */
@InheritConstructors
class EntityStartException extends Exception {

}
