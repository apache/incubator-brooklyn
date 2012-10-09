define([
    "model/app-tree"
], function (AppTree) {

    var apps = new AppTree.Collection
    apps.url = "fixtures/application-tree.json"
    apps.fetch({async:false})

    describe("model/app-tree", function () {

        it("loads fixture data", function () {
            expect(apps.length).toBe(2)
            var app1 = apps.at(0)
            expect(app1.get("name")).toBe("test")
            expect(app1.get("id")).toBe("riBZUjMq")
            expect(app1.get("type")).toBe(null)
            expect(app1.get("children").length).toBe(1)
            expect(app1.get("children")[0].name).toBe("tomcat1")
            expect(app1.get("children")[0].type).toBe("brooklyn.entity.webapp.tomcat.TomcatServer")
            expect(apps.at(1).get("children").length).toBe(2)
        })

        it("has working getDisplayName", function () {
            var app1 = apps.at(0)
            expect(app1.getDisplayName()).toBe("test:riBZUjMq")
        })

        it("has working hasChildren method", function () {
            expect(apps.at(0).hasChildren()).toBeTruthy()
        })

        it("returns AppTree.Collection for getChildren", function () {
            var app1 = apps.at(0),
                children = new AppTree.Collection(app1.get("children"))
            expect(children.length).toBe(1)
            expect(children.at(0).getDisplayName()).toBe("tomcat1:fXyyQ7Ap")
        })
    })
})