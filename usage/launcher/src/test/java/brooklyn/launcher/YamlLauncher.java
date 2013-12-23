package brooklyn.launcher;

import brooklyn.launcher.camp.SimpleYamlLauncher;

public class YamlLauncher {

    public static void main(String[] args) {
        SimpleYamlLauncher l = new SimpleYamlLauncher();
        l.setShutdownAppsOnExit(true);
        
        l.launchAppYaml("java-web-app-and-db-with-function.yaml");
//        l.launchAppYaml("java-web-app-and-memsql.yaml");
//        l.launchAppYaml("memsql.yaml");
//        l.launchAppYaml("classpath://mongo-blueprint.yaml");
    }

}
