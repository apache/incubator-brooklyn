define([
    "model/task-summary"
], function (TaskSummary) {

    describe("model/task-summary spec", function () {
        var tasks = new TaskSummary.Collection
        tasks.url = "fixtures/task-summary-list.json"
        tasks.fetch({async:false})

        it("loads the collection from 'fixtures/task-summary-list.json'", function () {
            var task = tasks.at(0)
            expect(task.get("entityId")).toBe("VzK45RFC")
            expect(task.get("displayName")).toBe("start")
            expect(task.get("rawSubmitTimeUtc")).toBe(1348663165550)
        })
    })
})