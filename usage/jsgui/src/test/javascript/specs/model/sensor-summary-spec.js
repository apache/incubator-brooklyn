define(['model/sensor-summary'], function (SensorSummary) {

    describe('SensorSummary model', function () {
        var sensorCollection = new SensorSummary.Collection
        sensorCollection.url = 'fixtures/sensor-summary-list.json'
        sensorCollection.fetch()

        it('collection must have 4 sensors', function () {
            expect(sensorCollection.length).toBe(4)
        })

        it('must have a sensor named service.state', function () {
            var filteredSensors = sensorCollection.where({ 'name':'service.state'})
            expect(filteredSensors.length).toBe(1)
            var ourSensor = filteredSensors.pop()
            expect(ourSensor.get("name")).toBe('service.state')
            expect(ourSensor.get("type")).toBe('brooklyn.entity.basic.Lifecycle')
            expect(ourSensor.get("description")).toBe('Service lifecycle state')
            expect(ourSensor.getLinkByName('self')).toBe('fixtures/service-state.json')
            expect(ourSensor.getLinkByName()).toBe(undefined)
        })
    })
})
