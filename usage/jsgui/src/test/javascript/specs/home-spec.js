/**
 * Jasmine test specs for Application Template and Template rendering. We test for HTML structure,
 * ID's, classes that we use, etc.
 *
 * Tests go like this:
 * - we test that all templates are on the page
 * - we test that the templates contain all the divs, buttons with the right id's and classes
 * - we test that rendering produces the right stuff
 */
define([
    "underscore", "jquery", "model/application", "model/location", "view/home"
], function (_, $, Application, Location, HomeView) {

    Backbone.View.prototype.callPeriodically = function (uid, callback, interval) {
        if (!this._periodicFunctions) {
            this._periodicFunctions = []
        }
        this._periodicFunctions.push(setInterval(callback, interval))
    }

    describe('view/home-spec', function () {

        describe('view/home HomePageView', function () {
            var view,
                apps = new Application.Collection,
                locs = new Location.Collection
            apps.url = "fixtures/application-list.json"
            apps.fetch({async:false})
            locs.url = "fixtures/location-list.json"
        	locs.fetch({async:false})

            beforeEach(function () {
                view = new HomeView({
                    collection:apps,
                    locations:locs,
                    offline:true
                }).render()
            })

            afterEach(function () {
                view.close()
            })

            it('should have a div#home-first-row and div#home-second-row', function () {
                expect(view.$('div.home-first-row').length).toEqual(1)
                expect(view.$('div.home-second-row').length).toEqual(1)
            })

            it('should contain an apps table and a new button', function () {
                expect(view.$('div#new-application-resource').length).toEqual(1)
                expect(view.$('div#applications').length).toEqual(1)
            })

            it('div#new-application-resource must have button#add-new-application-resource', function () {
                expect(view.$('div button#add-new-application').length).toEqual(1)
            })

            it('div#applications must have table with tbody#applications-table-body', function () {
                expect(view.$('div#applications table').length).toEqual(1)
                expect(view.$('div#applications tbody#applications-table-body').length).toEqual(1)
            })

            it('must have div#modal-container', function () {
                expect(view.$('div#modal-container').length).toEqual(1)
            })
        })

        describe('view/home ApplicationEntryView rendering', function () {
            var model = new Application.Model({
                status:'STARTING',
                spec:new Application.Spec({
                    name:'sample',
                    entities:[
                        { name:'entity-01',
                            type:'org.apache.TomcatServer'}
                    ],
                    locations:['/some/where/over/the/rainbow']
                })
            })
            var view = new HomeView.AppEntryView({
                model:model
            }).render()

            it('must have 3 td tags', function () {
                expect(view.$('td').length).toEqual(3)
            })

            it('must have a td with button.delete', function () {
                expect(view.$('button.delete').length).toEqual(1)
                expect(view.$('button.delete').parent().is('td')).toEqual(true)
                expect(view.$("button.delete").attr("id")).toBe(model.cid)
            })
        })
    })
})