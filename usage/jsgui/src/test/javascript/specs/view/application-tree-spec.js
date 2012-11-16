define([
    "underscore", "jquery", "model/app-tree", "view/application-tree", "text!tpl/apps/tree-item.html"
], function (_, $, AppTree, ApplicationTreeView) {

    var apps = new AppTree.Collection
    apps.url = 'fixtures/application-tree.json'
    apps.fetch({async:true})

    describe('view/application-tree renders the list of applications as a tree', function () {
        var view

        beforeEach(function () {
            view = new ApplicationTreeView({
                collection:apps
            }).render()
        })

        it("builds the entity tree for each application", function () {
            expect(view.$("#riBZUjMq").length).toBe(1)
            expect(view.$("#fXyyQ7Ap").length).toBe(1)
            expect(view.$("#child-02").length).toBe(1)
            expect(view.$("#child-03").length).toBe(1)
            expect(view.$("#child-04").length).toBe(1)
            
            expect(view.$("#child-nonesuch").length).toBe(0)
        })
    })
})