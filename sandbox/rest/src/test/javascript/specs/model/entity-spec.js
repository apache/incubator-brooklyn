define([
    "underscore", "model/entity"
], function (_, Entity) {

    describe("model/entity", function () {
        // keep these in describe so jasmine-maven will load them from the file pointed by URL
        var entityFixture = new Entity.Collection
        entityFixture.url = 'fixtures/entity.json'
        entityFixture.fetch({async:true})

        it('loads all model properties defined in fixtures/entity.json', function () {
            expect(entityFixture.length).toEqual(1)
            var entity = entityFixture.at(0)
            expect(entity.get("name")).toEqual('Vanilla Java App')
            expect(entity.get("type")).toEqual('brooklyn.entity.java.VanillaJavaApp')
            expect(entity.get("config")).toEqual({})
        })
    })
})