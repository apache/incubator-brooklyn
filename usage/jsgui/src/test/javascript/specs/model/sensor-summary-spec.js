/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
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
            expect(ourSensor.get("type")).toBe('org.apache.brooklyn.entity.lifecycle.Lifecycle')
            expect(ourSensor.get("description")).toBe('Service lifecycle state')
            expect(ourSensor.getLinkByName('self')).toBe('fixtures/service-state.json')
            expect(ourSensor.getLinkByName()).toBe(undefined)
        })
    })
})
