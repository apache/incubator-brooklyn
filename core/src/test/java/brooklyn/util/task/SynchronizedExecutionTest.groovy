package brooklyn.util.task

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import junit.extensions.RepeatedTest;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import brooklyn.management.ExecutionManager;
import brooklyn.management.Task;

class SynchronizedExecutionTest {
	List data = new ArrayList()
	
	/** accepts flags last and size */
	protected void assertDataInOrder(Map flags=[:]) {
		Object last = flags["last"]
		Object size = flags["size"]?:null
		
		if (size!=null) Assert.assertTrue("expected size $size but it is ${data.size}; list is $data", data.size()==size)
		
		Object lo = null
		for (Object o in data) {
			if (lo!=null) Assert.assertTrue("expected $o greater than $lo; list is $data", lo.compareTo(o) < 0)
			lo = o
		}
		if (last!=null) Assert.assertTrue("expected last element $last but it is $lo; list is $data", lo==last)
	}
	
	@Test
	public void runTwoSynchedTasks() {
		data.clear()
		BasicTask t1 = [ { data << 1;  } ]
		BasicTask t2 = [ { data << 2; } ]
		data << 0
		
		BasicExecutionManager em = []
		em.setTaskPreprocessorForTag("category1", SingleThreadedExecution.class);
		
		em.submit tag:"category1", t1
		em.submit tag:"category1", t2

		t2.get()
		assertDataInOrder(size:3, last:2)
	}

	@Test
	public void runTwoSynchedTasks100Times() {
		100.times { runTwoSynchedTasks() }
	}
}
