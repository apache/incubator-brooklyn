---
title: Monitoring and Managing Applications
title_in_menu: Monitoring and Managing Applications
layout: website-normal
menu_parent: index.md
---

Click on the application name, or open the Applications tab.

We can explore the management hierarchy of the application, which will show us the entities it is composed of.

 * My Web Cluster (A `BasicApplication`)
     * My DB (A `MySqlNode`)
     * My Web (A `ControlledDynamicWebAppCluster`)
        * Cluster of TomcatServer (A `DynamicWebAppCluster`)
        * NginxController (An `NginxController`)



Clicking on the "My Web Cluster" entity will show the "Summary" tab,
giving a very high level of what that component is doing. 
Click on each of the child components in turn for more detail on that component. 
Note that the cluster of web servers includes a "quarantine group", to which members of the 
cluster that fail will be added. These are excluded from the load-balancer's targets.

[![Exploring My Web.](images/my-web.png)](images/my-web-large.png)


## Activities

The Activity tab allows us to drill down into the tasks each entity is currently executing or has recently completed. It is possible to drill down through all child tasks, and view the commands issued, along with any errors or warnings that occurred.

For example clicking on the NginxController in the left hand tree and opening its Activity tab you can observe the 'start' task is 'In progress'.

**Note**: You may observe different tasks depending on how far your deployment has progressed).

[![My DB Activities Step 1.](images/my-db-activities-step1.png)](images/my-db-activities-step1-large.png)

Clicking on the 'start' task you can discover more details on the actions being carried out by that task (a task may consist of additional subtasks).

[![My DB Activities Step 2.](images/my-db-activities-step2.png)](images/my-db-activities-step2-large.png)

Continuing to drill down into the 'In progress' tasks you will eventually reach the currently active task where you can investigate the ssh command executed on the target node including the current stdin, stdout and stderr output.

[![My DB Activities Step 3.](images/my-db-activities-step3.png)](images/my-db-activities-step3-large.png)


## Sensors

Now click on the "Sensors" tab:
these data feeds drive the real-time picture of the application.
As you navigate in the tree at the left, you can see more targeted statistics coming in in real-time.

Explore the sensors and the tree to find the URL where the _NginxController_ for the webapp we just deployed is running. This can be found in '**My Web Cluster** -> **My Web** -> **NginxController** -> **_main.uri_**'.

Quickly return to the **‘Brooklyn JS REST client’** web browser
tab showing the "Sensors" and observe the '**My Web Cluster** -> **My Web** -> **Cluster of TomcatServer** -> **_webapp.reqs.perSec.last_**' sensor value increase.  



## Stopping the Application

To stop an application, select the application in the tree view (the top/root entity), click on the Effectors tab, and invoke the "Stop" effector. This will cleanly shutdown all components in the application and return any cloud machines that were being used.

[![My DB Activities.](images/my-web-cluster-stop-confirm.png)](images/my-web-cluster-stop-confirm-large.png)


### Next

Brooklyn's real power is in using **[Policies](policies.html)**  to automatically *manage* applications. 
