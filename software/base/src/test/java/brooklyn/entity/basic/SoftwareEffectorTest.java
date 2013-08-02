package brooklyn.entity.basic;

import brooklyn.entity.Effector;
import brooklyn.management.Task;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.task.Tasks;

public class SoftwareEffectorTest {

    // TODO see SoftwareTasks
    
//    public static Effector<Void> SAY_FOO = EntityTasks.effector(Void.class, "sayFoo").impl(new EntityTaskFactory<Void>() {
//        public Task newTask() {
//            // old-skool java
//            TaskBuilder tb = Tasks.builder();
//            tb.add(SoftwareTasks.ssh("echo foo"));
//            tb.add(SoftwareTasks.ssh("echo bar"));
//            TaskBuilder copies = Tasks.builder().parallel(true);
//            copies.add(SoftwareTasks.scp("file1"));
//            copies.add(SoftwareTasks.scp("file2"));
//            tb.add(Tasks.ifLastResult(0, copies.build()));
//            return tb.build();
//        }
//        
//        // not implemented
////        public Task newInstance2() {
////            // fluent style (kinda like jquery)
////            return SoftwareTasks.ssh("echo foo").ssh("echo bar").
////                    ifResult(0).
////                        parallel().
////                            scp("file1").scp("file2").
////                        end().
////                    end().
////                build();
////        }
//        
//        public void newTask(ConfigBag parameters) {
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
//
//    {
//        addSensor(BLAH_BLAH);
//        addEffector(SAY_FOO);
//        addEffector(SAY_BAR, new EffectorBody() {
//            public void build() {
//                add(ssh(XXX));
//                add(ssh(XXX));
//                add(ssh(XXX));
//            }
//        });
//        addEnricher(XXX);
//    }
    
}
