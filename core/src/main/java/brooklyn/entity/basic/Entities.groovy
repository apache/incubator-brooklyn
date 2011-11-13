package brooklyn.entity.basic;

import java.io.Writer
import java.util.Collection
import java.util.List
import java.util.Map

import brooklyn.entity.ConfigKey
import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor
import brooklyn.management.Task
import brooklyn.util.task.ParallelTask

/** Convenience methods for working with entities. 
 * Also see the various *Methods classes for traits 
 * (eg StartableMethods for Startable implementations). */
public class Entities {
	
	public static Collection<ConfigKey<?>> getConfigKeys(Entity e) {
		if (e in EntityLocal) return ((EntityLocal)e).getConfigKeys().values();
		else return e.getEntityClass().getConfigKeys();
	}
	public static Collection<Sensor<?>> getSensors(Entity e) {
		if (e in EntityLocal) return ((EntityLocal)e).getSensors().values();
		else return e.getEntityClass().getSensors();
	}
	public static Collection<Effector<?>> getEffectors(Entity e) {
		if (e in EntityLocal) return ((EntityLocal)e).getEffectors().values();
		else return e.getEntityClass().getEffectors();
	}

	/** invokes the given effector with the given named arguments on the entitiesToCall, from the calling context of the callingEntity;
	 * intended for use only from the callingEntity
	 * @return ParallelTask containing a results from each invocation; calling get() on the result will block until all complete,
	 * and throw error if any threw error   
	 */
	public static <T> Task<List<T>> invokeEffectorList(EntityLocal callingEntity, Collection<Entity> entitiesToCall, Effector<T> effector, Map<String,?> parameters=[:]) {
		if (!entitiesToCall || entitiesToCall.isEmpty()) return null
		List<Task> tasks = entitiesToCall.collect { it.invoke(effector, parameters) }
		ParallelTask invoke = new ParallelTask(tasks)
		callingEntity.executionContext.submit(invoke)
		return invoke
	}

	public static void dumpInfo(Entity e, Writer out=new PrintWriter(System.out), String currentIndentation="", String tab="  ") {
		out << currentIndentation+e.toString()+"\n"
		
		getConfigKeys(e).each {
			out << currentIndentation+tab+tab+it.name;
			out << ": "+e.getConfig(it)+"\n"
		}
		getSensors(e).each {
			out << currentIndentation+tab+tab+it.name;
			if (it in AttributeSensor) out << ": "+e.getAttribute(it)
			out << "\n"
		}
		e.getOwnedChildren().each {
			dumpInfo(it, out, currentIndentation+tab, tab)
		}
		out.flush()
	}

	public static boolean isAncestor(Entity descendant, Entity potentialAncestor) {
		AbstractEntity ancestor = descendant.getOwner()
		while (ancestor) {
			if (ancestor.equals(potentialAncestor)) return true
			ancestor = ancestor.getOwner()
		}
		return false
	}

	/** note, it is usually preferred to use isAncestor() and swap the order, it is a cheaper method */
	public static boolean isDescendant(Entity ancestor, Entity potentialDescendant) {
		Set<Entity> inspected = [] as HashSet
		List<Entity> toinspect = [ancestor]
		
		while (!toinspect.isEmpty()) {
			Entity e = toinspect.pop()
			if (e.getOwnedChildren().contains(potentialDescendant)) {
				return true
			}
			inspected.add(e)
			toinspect.addAll(e.getOwnedChildren())
			toinspect.removeAll(inspected)
		}
		
		return false
	}

}
