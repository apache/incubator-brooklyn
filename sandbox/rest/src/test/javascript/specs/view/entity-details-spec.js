define([
    "underscore", "jquery", "backbone", "model/entity-summary", "view/entity-details", "view/entity-summary",
    "view/entity-sensors", "model/application"
], function (_, $, Backbone, EntitySummary, EntityDetailsView, EntitySummaryView, EntitySensorsView, Application) {

    EntitySummary.Model.prototype.getSensorUpdateUrl = function () {
        return "fixtures/sensor-current-state.json"
    }

    Backbone.View.prototype.callPeriodically = function (callback, interval) {
        if (!this._periodicFunctions) {
            this._periodicFunctions = []
        }
        this._periodicFunctions.push(setInterval(callback, interval))
    }

    describe('view/entity-details-spec EntityDetailsView', function () {
        var entity, view, app

        beforeEach(function () {
            entity = new EntitySummary.Model
            entity.url = 'fixtures/entity-summary.json'
            entity.fetch({async:false})
            app = new Application.Model
            app.url = "fixtures/application.json"
            app.fetch({async:false})
            view = new EntityDetailsView({
                model:entity,
                application:app
            }).render()
        })

        it('renders to a bootstrap tabbable', function () {
            expect(view.$('#details-header').length).toBe(1)
            expect(view.$('#summary').length).toBe(1)
            expect(view.$('#sensors').length).toBe(1)
            expect(view.$('#effectors').length).toBe(1)
        })
    })

    describe('EntitySummaryView', function () {
        var entity, view, app

        beforeEach(function () {
            entity = new EntitySummary.Model
            entity.url = 'fixtures/entity-summary.json'
            entity.fetch({async:false})
            app = new Application.Model
            app.url = "fixtures/application.json"
            app.fetch({async:false})
            view = new EntitySummaryView({
                model:entity,
                application:app
            }).render()
        })

        it('must render as 2 p tags', function () {
            expect(view.$("textarea").length).toBe(2)
            expect(view.$("h3").length).toBe(2)
        })
    })

    describe('EntitySensorsView', function () {
        var sampleEntity, view

        beforeEach(function () {
            sampleEntity = new EntitySummary.Model
            sampleEntity.url = 'fixtures/entity-summary.json'
            sampleEntity.fetch({async:false})
            view = new EntitySensorsView({ model:sampleEntity}).render()
        })

        it('must render as a table with sensor data', function () {
            var $body
            expect(view.$('table#sensors-table').length).toBe(1)
            expect(view.$('th').length).toBe(3)
            $body = view.$('tbody')

            expect($body.find('tr:first .sensor-name').html()).toBe('jmx.context')
            expect($body.find('tr:first .sensor-name').attr('title')).toBe("JMX context path")
            expect($body.find('tr:last .sensor-name').attr('title')).toBe("Suggested shutdown port")
            expect($body.find("tr:last .sensor-name").attr("rel")).toBe("tooltip")
        })
    })
})