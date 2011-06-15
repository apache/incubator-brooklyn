package org.overpaas.execution

import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.overpaas.entities.Entity

class ExecutionManager {
	
	ExecutorService runner = Executors.newCachedThreadPool() 
	
	Set<Task> knownTasks = new LinkedHashSet()
	
	
	
	Task submit(Entity entity, Task task) {
		if (task.result!=null) return task
		synchronized (task) {
			if (task.result!=null) return task
			task.result = runner.submit task.job
			return task
		}
	}
	

//	CompoundTask execute(Entity entity, CompoundTask tasks) {
//		tasks.subTasks.each { it.run(runner) }
//		tasks
//	}
	
}
