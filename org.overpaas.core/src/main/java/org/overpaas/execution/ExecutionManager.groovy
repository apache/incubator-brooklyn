package org.overpaas.execution

import java.util.concurrent.Executor
import java.util.concurrent.Executors;

import org.overpaas.entities.Entity

class ExecutionManager {
	
	Executor runner = Executors.newCachedThreadPool() 
	
	CompoundTask execute(Entity entity, CompoundTask tasks) {
		tasks.subTasks.each { it.run(runner) }
		tasks
	}
}
