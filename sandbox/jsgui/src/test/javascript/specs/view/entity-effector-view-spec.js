define([
    "underscore", "jquery", "backbone", "view/entity-effectors", "model/entity-summary"
], function (_, $, Backbone, EntityEffectorsView, EntitySummary) {

    var entitySummary = new EntitySummary.Model
    entitySummary.url = "fixtures/entity-summary.json"

    entitySummary.fetch({success:function () {

        var $ROOT = $("<div/>"),
            entityEffectorsView = new EntityEffectorsView({
                el:$ROOT,
                model:entitySummary
            })

        describe("view/entity-effectors", function () {

            it("has table#effectors-table after initialization", function () {
                expect($ROOT.find('table#effectors-table').length).toBe(1)
            })

            it("renders 3 effectors in the table", function () {
                entityEffectorsView.render()
                var $table = $ROOT.find("tbody")
                expect($table.find("tr").length).toBe(3)
                expect($table.find("tr:first .effector-name").html()).toBe("start")
                expect($table.find("tr:last .effector-name").html()).toBe("stop")
            })
        })
    }})
})