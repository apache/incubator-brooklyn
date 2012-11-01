define([
    "underscore", "model/location"
], function (_, Location) {

    var location = new Location.Model
    location.url = "fixtures/location-summary.json"
    location.fetch({async:false})

    describe('model/location', function () {
        it("loads data from fixture file", function () {
            expect(location.get("provider")).toBe("localhost")
            expect(location.getLinkByName("self")).toBe("/v1/locations/123")
        })
    })

    describe('model/location', function () {
        // keep these in describe so jasmine-maven will load them from the file pointed by URL
        var locationFixtures = new Location.Collection
        locationFixtures.url = 'fixtures/location-list.json'
        locationFixtures.fetch()
        it('loads all model properties defined in fixtures/location-list.json', function () {
            expect(locationFixtures.length).toEqual(1)
            var spec = locationFixtures.at(0)
            expect(spec.get("provider")).toEqual('localhost')
            expect(spec.get("config")).toEqual({})
            expect(spec.hasSelfUrl('/v1/locations/123')).toBeTruthy()
            expect(spec.getLinkByName("self")).toEqual('/v1/locations/123')
        })

        var locationCollection = new Location.Collection()
        it('fetches from /v1/locations', function () {
            expect(locationCollection.url).toEqual('/v1/locations')
        })
        it('has model LocationSpec', function () {
            expect(locationCollection.model).toEqual(Location.Model)
        })
    })
})
