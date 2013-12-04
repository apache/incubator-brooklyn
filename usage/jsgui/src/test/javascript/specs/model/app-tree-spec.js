define([
    "model/app-tree"
], function (AppTree) {

    /** TODO the application-tree.json is hacked together and out of date, 
     *  reflects a combo of what comes back from server and what used to come back and was expected */
    
    $.ajaxSetup({ async:false });
    var apps = new AppTree.Collection
    apps.url = "fixtures/application-tree.json"
    apps.fetch({ async:false })
    
    describe("model/app-tree", function () {

        it("loads fixture data", function () {
            expect(apps.length).toBe(2)
            var app1 = apps.at(0)
            expect(app1.get("name")).toBe("test")
            expect(app1.get("id")).toBe("riBZUjMq")
            expect(app1.get("type")).toBe("")
            expect(app1.get("children").length).toBe(1)
            expect(app1.get("children")[0].name).toBe("tomcat1")
            expect(app1.get("children")[0].type).toBe("brooklyn.entity.webapp.tomcat.TomcatServer")
            expect(apps.at(1).get("children").length).toBe(2)
        })

        it("has working getDisplayName", function () {
            var app1 = apps.at(0)
            expect(app1.getDisplayName()).toBe("test")
        })

        it("has working hasChildren method", function () {
            expect(apps.at(0).hasChildren()).toBeTruthy()
        })

        it("returns AppTree.Collection for getChildren", function () {
            var app1 = apps.at(0),
                children = new AppTree.Collection(app1.get("children"))
            expect(children.length).toBe(1)
            expect(children.at(0).getDisplayName()).toBe("tomcat1")
        })

        it("returns entity names for ids", function() {
            expect(apps.getEntityNameFromId("fXyyQ7Ap")).toBe("tomcat1");
            expect(apps.getEntityNameFromId("child-02")).toBe("tomcat2");
            expect(apps.getEntityNameFromId("child-04")).toBe("tomcat04");
            expect(apps.getEntityNameFromId("nonexistant")).toBeFalsy();
        });
    })
})