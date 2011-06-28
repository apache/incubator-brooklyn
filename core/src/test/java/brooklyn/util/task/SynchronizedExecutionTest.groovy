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

	public static class SingleThreadedExecution {
		Queue<String> order = new ConcurrentLinkedQueue<String>() 
		
		ExecutionManager manager;
		Object tag;
		
		public void injectManager(ExecutionManager manager) {
			this.manager = manager;
		}
		public void injectTag(Object tag) {
			this.tag = tag;
		}
		public void onSubmit(Map flags=[:], Task task) {
			order.add(task.id)
		}
		public void onStart(Map flags=[:], Task task) {
			def next = order.peek();
			while (next!=task.id) {
				task.blockingDetails = "single threaded category, "+order.size()+" elements ahead of us when submitted"
				synchronized (task.id) {
					task.id.wait();
				}
				next = order.peek();
			}
			task.blockingDetails = null
		}
		public void onEnd(Map flags=[:], Task task) {
			def last = order.remove()
			assert last == task.id
			def next = order.peek()
			if (next!=null) synchronized (next) { next.notifyAll() }
		}
	}
	
//	@Ignore
	@Test
	public void runTwoSynchedTasks() {
		SingleThreadedExecution syn = new SingleThreadedExecution()
		Task[] tasks = new Task[2];
		data.clear()
		BasicTask t1 = [ { println "1 - pre onStart"; syn.onStart(tasks[0]); Thread.sleep(1000); println "do 1 - "+System.currentTimeMillis(); data << 1; syn.onEnd(tasks[0]) } ]
		BasicTask t2 = [ { println "2 - pre onStart"; syn.onStart(tasks[1]); Thread.sleep(1000); println "do 2 - "+System.currentTimeMillis(); data << 2; syn.onEnd(tasks[1]) } ]
		data << 0
		BasicExecutionManager em = []
//		em.addTagPreprocessor("category1", SingleThreadedExecution.class);
		
		println "000\n  1: "+t1.getStatusString(2)+"\n  2: "+t2.getStatusString(2)
		
		syn.injectManager(em)
		syn.injectTag("category1")
		
		tasks[0] = t1;
		syn.onSubmit(tasks[0])
		em.submit tag:["category1"], t1
		
		tasks[1] = t2;
		syn.onSubmit(t2)
		em.submit tag:["category1"], t2

		println "AAA\n  1: "+t1.getStatusDetail(false)+"\n  2: "+t2.getStatusDetail(false)
		Thread.sleep(1500)
		println "BBB\n  1: "+t1.getStatusDetail(true)+"\n  2: "+t2.getStatusDetail(false)
		
		t1.get()
		t2.get()
		println "CCC\n  1: "+t1.getStatusDetail(true)+"\n  2: "+t2.getStatusDetail(true)
		assertDataInOrder(size:3, last:2)
	}

	@Ignore
	@Test
	public void runTwoSynchedTasks100Times() {
		100.times { runTwoSynchedTasks() }
	}
}
