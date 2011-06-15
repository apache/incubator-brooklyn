package org.overpaas.execution

import java.util.concurrent.Callable;
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.overpaas.entities.Entity

class ExecutionManager {
	
	ExecutorService runner = Executors.newCachedThreadPool() 
	
	Set<Task> knownTasks = new LinkedHashSet()
	Map<Object,Set<Task>> tasksByBucket = new LinkedHashMap()
	
	public Set<Task> getTasksByBucket(Object bucket) { return tasksByBucket.get(bucket) ?: Collections.emptySet() }
	public Set<Task> getTaskBuckets() { synchronized (tasksByBucket) { return new LinkedHashSet(tasksByBucket.keySet()) }}
	
	public Task submit(Object bucket, Runnable r) { submit bucket, new Task(r) }
	public Task submit(Object bucket, Callable r) { submit bucket, new Task(r) }
	public Task submit(Object bucket, Task task) {
		if (task.result!=null) return task
		synchronized (task) {
			if (task.result!=null) return task
			synchronized (knownTasks) { 
				knownTasks << task }
			synchronized (tasksByBucket) { 
				Set set = tasksByBucket.get bucket; 
				if (set==null) {
					set = new LinkedHashSet()
					tasksByBucket.put bucket, set
				}
				set << task
			}
			task.result = runner.submit(task.job as Callable)
			return task
		}
	}
	
	
//	CompoundTask execute(Entity entity, CompoundTask tasks) {
//		tasks.subTasks.each { it.run(runner) }
//		tasks
//	}
	
}
