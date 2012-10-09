/*
 * Variables that start with $ are usually jQuery objects.
 *
 */
define([
    "underscore", "jquery", "model/location", "view/location-modal", "view/catalog"
], function (_, $, Location, LocationModalView, CatalogResourceView) {

    describe('Catalog UI', function () {

        var locationCollection = new Location.Collection()
        var catalogResourceView = new CatalogResourceView({
            model:locationCollection
        })

        describe('CatalogResourceView', function () {

            var $rendering = catalogResourceView.render().$el
            var $entityModal = $rendering.find('div#new-entity-modal')
            var $locationModal = $rendering.find('div#new-location-modal')
            var $catalogResource = $rendering.find('div#catalog-resource')
            var $entityForm = $rendering.find('form#new-entity-form')

            it('must render a div#catalog-resource and two modals', function () {
                expect($catalogResource.length).toEqual(1)
                expect($entityModal.length).toEqual(1)
                expect($locationModal.length).toEqual(1)
            })

            describe('div#catalog-resource', function () {

                it('must have button#add-new-entity and button#add-new-location', function () {
                    expect($catalogResource.find('button#add-new-entity').length).toEqual(1)
                    expect($catalogResource.find('button#add-new-location').length).toEqual(1)
                })

                it('must contain div#entities under div#catalog-resources', function () {
                    expect($rendering.find('div#catalog-resource div#entities').length).toEqual(1)
                })
                it('must contain div#policies under div#catalog-resources', function () {
                    expect($rendering.find('div#catalog-resource div#policies').length).toEqual(1)
                })
                it('must contain ul elements inside each #entities and #policies', function () {
                    expect($rendering.find('#entities ul').length).toEqual(1)
                    expect($rendering.find('#policies ul').length).toEqual(1)
                })
                it('div#locations must contain table#locations with tbody#locations-table-body', function () {
                    var $locationsDiv = $catalogResource.find('div#locations')
                    expect($locationsDiv.find('table#locations-table').length).toEqual(1)
                    expect($locationsDiv.find('tbody#locations-table-body').length).toEqual(1)
                })
            })

            describe('div#new-entity-modal', function () {
                it('must have a proper bootstrap modal window as a div with id new-entity-modal and a form', function () {
                    expect($entityModal.length).toEqual(1)
                    expect($entityModal.is('.modal.hide.fade')).toEqual(true)
                    expect($entityModal.find('div.modal-header').length).toEqual(1)
                    expect($entityModal.find('div.modal-body').length).toEqual(1)
                    expect($entityModal.find('div.modal-footer').length).toEqual(1)
                    expect($entityForm.length).toEqual(1)
                })
                it('the form#new-entry-form must have an file input field, and two buttons', function () {
                    expect($entityForm.find('input#groovy-code').length).toEqual(1)
                    expect($entityForm.find('button').length).toEqual(3)
                    expect($entityForm.find('button#new-entity-submit').length).toEqual(1)
                })
            })

            describe('div#new-location-modal', function () {
                it('is a bootstrap modal', function () {
                    expect($locationModal.is('.modal.hide.fade')).toEqual(true)
                })
            })
        })

        var testsForNewLocationModal = {
            hasBootstrapModalElements:function ($element) {
                return function () {
                    expect($element.find('div.modal-header').length).toEqual(1)
                    expect($element.find('div.modal-body').length).toEqual(1)
                    expect($element.find('div.modal-footer').length).toEqual(1)
                }
            },
            hasButtons:function ($element) {
                return function () {
                    expect($element.find('button').length).toEqual(5)
                    expect($element.find('button#add-location-config').length).toEqual(1)
                    expect($element.find('button#new-location-submit').length).toEqual(1)
                    expect($element.find('button#show-config-form').length).toEqual(1)
                }
            },
            hasConfigInputs:function ($element) {
                return function () {
                    expect($element.find('input').length).toEqual(3)
                    expect($element.find('input#provider').length).toEqual(1)
                }
            },
            hasInfoMessageDiv:function ($element) {
                return function () {
                    var $div = $element.find('div.info-message')
                    expect($div.length).toEqual(1)
                    expect($div.is('.hide')).toBeTruthy()
                    expect($div.find('p').length).toEqual(1)
                }
            }
        }

        describe('New location modal rendering', function () {
            var $newLocationRendering = new LocationModalView({model:new Location.Model}).render().$el

            it('must have bootstrap modal components',
                testsForNewLocationModal.hasBootstrapModalElements($newLocationRendering))

            it('must have 4 buttons: 2 x close, save, add-config',
                testsForNewLocationModal.hasButtons($newLocationRendering))

            it('must have input for name and one key-value pair',
                testsForNewLocationModal.hasConfigInputs($newLocationRendering))

            it('must have a hidden div.info-message',
                testsForNewLocationModal.hasInfoMessageDiv($newLocationRendering))
        })
    })
})