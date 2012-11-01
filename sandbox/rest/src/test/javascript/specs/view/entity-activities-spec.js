define([
    "model/task-summary", "view/entity-activities"
], function (TaskSummary, ActivityView) {

    describe("view/entity-activity", function () {
        var entity, view

        beforeEach(function () {
            entity = new Entity()
            entity.url = "fixtures/entity-summary.json"
            entity.fetch({async:false})
            view = new ActivityView({ model:entity})
        })
    })

    describe("view/entity-activity details modal view", function () {
        var tasks, view

        beforeEach(function () {
            tasks = new TaskSummary.Collection
            tasks.url = "fixtures/task-summary-list.json"
            tasks.fetch({async:false})
            view = new ActivityView.Modal({
                model:tasks.at(0)
            }).render()
        })

        it("renders as a bootstrap modal", function () {
            expect(view.$el.is("div.modal")).toBe(true)
        })
    })
})