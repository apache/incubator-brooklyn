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

})