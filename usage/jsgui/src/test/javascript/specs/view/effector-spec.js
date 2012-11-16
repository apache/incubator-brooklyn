define([
    "underscore", "view/effector", "model/effector-summary", "model/entity"
], function (_, EffectorView, EffectorSummary, Entity) {

    var modalView, collection = new EffectorSummary.Collection()
    collection.url = "fixtures/effector-summary-list.json"
    collection.fetch()
    
    var entityFixture = new Entity.Collection
    entityFixture.url = 'fixtures/entity.json'
    entityFixture.fetch({async:true})

    modalView = new EffectorView({
        tagName:"div",
        className:"modal",
        model:collection.at(0),
        entity:entityFixture.at(0)
    })

    describe("view/effector", function () {
        // render and keep the reference to the view
        modalView.render()
        it("must render a bootstrap modal", function () {
            expect(modalView.$(".modal-header").length).toBe(1)
            expect(modalView.$(".modal-body").length).toBe(1)
            expect(modalView.$(".modal-footer").length).toBe(1)
        })

        it("must have effector name, entity name, and effector description in header", function () {
            expect(modalView.$(".modal-header h3").html()).toContain("start")
            expect(modalView.$(".modal-header h3").html()).toContain("Vanilla")
            expect(modalView.$(".modal-header p").html()).toBe("Start the process/service represented by an entity")
        })

        it("must have the list of parameters in body", function () {
            expect(modalView.$(".modal-body table").length).toBe(1)
            expect(modalView.$(".modal-body tr").length).toBe(2) // one tr from the head
            expect(modalView.$(".modal-body .param-name").html()).toBe("locations")
        })
        it("must have two buttons in the footer", function () {
            expect(modalView.$(".modal-footer button").length).toBe(2)
            expect(modalView.$(".modal-footer button.invoke-effector").length).toBe(1)
        })

        it("must properly extract parameters from table", function () {
            var params = modalView.extractParamsFromTable()
            expect(params["locations"]).toBe(null)
            expect(params).toEqual({"locations":null})
        })
    })
})