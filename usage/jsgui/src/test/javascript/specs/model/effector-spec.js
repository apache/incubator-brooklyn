define([
    "model/effector-summary", "model/effector-param"
], function (EffectorSummary, EffectorParam) {
    $.ajaxSetup({ async:false });
    
    describe("effector-spec: EffectorSummary model", function () {
        var effectorCollection = new EffectorSummary.Collection
        effectorCollection.url = "fixtures/effector-summary-list.json"
        effectorCollection.fetch()

        it("must have 3 objects", function () {
            expect(effectorCollection.length).toBe(3)
        })

        it("has a first object 'name'", function () {
            var effector1 = effectorCollection.at(0)
            expect(effector1.get("name")).toBe("start")
            expect(effector1.get("returnType")).toBe("void")
            expect(effector1.get("parameters").length).toBe(1)
        })

        it(" effector1 has a first parameter named 'locations'", function () {
            var effector1 = effectorCollection.at(0)
            var param1 = new EffectorParam.Model(effector1.getParameterByName("locations"))
            expect(param1.get("name")).toBe("locations")
            expect(param1.get("type")).toBe("java.util.Collection")
            expect(param1.get("description")).toBe("")
        })
    })
})
