define(
    ["model/entity-summary" ],
    function (EntitySummary) {

        describe('model/entity-summary EntitySummary model and collection', function () {
            var summaries = new EntitySummary.Collection
            summaries.url = 'fixtures/entity-summary-list.json'
            summaries.fetch({async:false})
            var eSummary = summaries.at(0)

            it('the collection element must be of type TomcatServer and have expected properties', function () {
                expect(eSummary.getLinkByName('catalog'))
                    .toBe('/v1/catalog/entities/brooklyn.entity.webapp.tomcat.TomcatServer')
                expect(eSummary.get("type")).toBe('brooklyn.entity.webapp.tomcat.TomcatServer')
                expect(eSummary.getLinkByName('sensors')).toBe('fixtures/sensor-summary-list.json')
                expect(eSummary.getDisplayName()).toBe('TomcatServer:zQsqdXzi')
            })

            it('collection has working findByDisplayName function', function () {
                expect(summaries.findByDisplayName('test').length).toBe(0)
                expect(summaries.findByDisplayName(eSummary.getDisplayName()).length).toBe(1)
                expect(JSON.stringify(summaries.findByDisplayName(eSummary.getDisplayName()).pop().toJSON())).toBe(JSON.stringify(eSummary.toJSON()))
            })

            it('collection must have one element', function () {
                expect(summaries.length).toBe(1)
            })

        })
    })