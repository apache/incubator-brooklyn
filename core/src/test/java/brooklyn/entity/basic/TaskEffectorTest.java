package brooklyn.entity.basic;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

import brooklyn.entity.Effector;
import brooklyn.entity.trait.Startable;
import brooklyn.management.Task;
import brooklyn.util.task.Tasks;

public class TaskEffectorTest {

//    public abstract static class EffectorTaskFactory<T> {
//        protected EntityInternal entity() { return null; }
//        public Effector effector() { return null; }
//        public abstract Task<T> newInstance();
//    }
//    
////    public ExplicitEffector(String name, Class<T> type, List<ParameterType<?>> parameters, String description)
//    
//    MethodEffector<Void> START = new MethodEffector<Void>(Startable.class, "start");
//    
//    public static Effector SAY_FOO = EffectorTask.builder(Void.class, "sayFoo").impl(new EntityTaskFactory() {
//        public Task newInstance1() {
//            // old-skool java
//            TaskBuilder tb = TaskBuilder.newInstance();
//            tb.add(Tasks.ssh("echo foo"));
//            tb.add(Tasks.ssh("echo bar"));
//            TaskBuilder copies = TaskBuilder.newParallelInstance();
//            copies.add(Tasks.scp("file1"));
//            copies.add(Tasks.scp("file2"));
//            tb.add(Tasks.ifResult(0, copies.build()));
//            return tb.build();
//        }
//        public Task newInstance2() {
//            // fluent style (kinda like jquery)
//            return EntityTasks.ssh("echo foo").ssh("echo bar").
//                    ifResult(0).
//                        parallel().
//                            scp("file1").scp("file2").
//                        end().
//                    end().
//                build();
//        }          
//        public void run(ConfigBag parameters) {
//            // inherited style
//            // these methods generate tasks which get added to an implicit EffectorTask context
//            // this makes for more natural-looking code, but at some confusion cost
//            // (IOW -- the task-collection is hidden and there is magic, but is it worth it for ease-of-writing?)
//            ssh("echo foo");
//            ssh("echo bar");
//            ifLastResult(0).then(parallel(scp("file1"), scp("file2")));
//                    
//            // the framework could even allow blocking inside the code, ie
//            int result1 = ssh("echo bar").get();
//            if (result1 == 0) {
//                parallel(scp("file1"), scp("file2"));
//            }
//
//        }
//    }).parameter(String.class, "name").build();
    
}
