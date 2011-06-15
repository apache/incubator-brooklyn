package org.overpaas.execution

import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.Future

class Task {
	Runnable task
	Future future
	
	public Task(Runnable task) {}
	public Task(Callable task) {}
	public Task(Closure task) {}
	
	public void run(Executor runner) {
		future = runner.execute task
	}
	
}
