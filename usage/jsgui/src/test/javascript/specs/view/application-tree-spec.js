define([
    "underscore", "jquery", "model/app-tree", "view/application-tree",
    "model/entity-summary", "model/application"
], function (_, $, AppTree, ApplicationTreeView, EntitySummary, Application) {

    var apps = new AppTree.Collection
    apps.url = 'fixtures/application-tree.json'
    apps.fetch({async:true})

    describe('view/application-tree renders the list of applications as a tree', function () {
        var view, entityFetch, applicationFetch;

        beforeEach(function () {
            // ApplicationTree makes fetch requests to EntitySummary and Application models
            // with hard-coded URLs, causing long stacktraces in mvn output. This workaround
            // turns their fetch methods into empty functions.
            entityFetch = EntitySummary.Model.prototype.fetch;
            applicationFetch = Application.Model.prototype.fetch;
            EntitySummary.Model.prototype.fetch = Application.Model.prototype.fetch = function() {};

            view = new ApplicationTreeView({
                collection:apps
            }).render()
        })

        // Restore EntitySummary and Application fetch.
        afterEach(function() {
            EntitySummary.Model.prototype.fetch = entityFetch;
            Application.Model.prototype.fetch = applicationFetch;
        });

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