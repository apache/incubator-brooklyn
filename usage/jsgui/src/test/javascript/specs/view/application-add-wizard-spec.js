/**
 * Test the ModalWizard can build a modal to view, edit and submit an application.
 */
define([
    "underscore", "jquery", "backbone", "view/application-add-wizard", "model/application", "model/location"
//    "text!tpl/home/step1.html", "text!tpl/home/step2.html", "text!tpl/home/step3.html",
//    "text!tpl/home/step1-location-row.html", "text!tpl/home/step1-location-option.html",
//    "text!tpl/home/step2-entity-entry.html", "text!tpl/home/step2-config-entry.html"
], function (_, $, Backbone, AppAddWizard, Application, Locations
//        , 
//        Entities,
//      Step1Html, Step2Html, Step3Html,
//      Step1LocationRowHtml, LocationOptionHtml,  
//      Step2EntityEntryHtml, Step2ConfigEntryHtml
        ) {

    /* TEST disabled until we can more cleanly supply javascript.
     * should probably move to have one big model,
     * rather than passing around lots of small model items.
     */
    
//    Backbone.View.prototype.close = function () {
//        if (this.beforeClose) {
//            this.beforeClose()
//        }
//        this.remove()
//        this.unbind()
//    }
//
//    var fakeRouter = new Backbone.Router()
//    var modal = new ModalWizard({appRouter:fakeRouter});
//
//    describe("view/modal-wizard", function () {
//
//        it("creates an empty Application.Spec", function () {
//            expect(modal.model.get("name")).toBe("")
//            expect(modal.model.get("entities")).toEqual([])
//            expect(modal.model.get("locations")).toEqual([])
//        })
//
//        it("creates a view for each of the 3 steps", function () {
//            expect(modal.steps.length).toBe(3)
//            expect(modal.steps[0].view instanceof ModalWizard.Step1).toBeTruthy()
//            expect(modal.steps[1].view instanceof ModalWizard.Step2).toBeTruthy()
//            expect(modal.steps[2].view instanceof ModalWizard.Step3).toBeTruthy()
//        })
//
//        it("beforeClose method closes all 3 subviews", function () {
//            spyOn(Backbone.View.prototype, "close").andCallThrough()
//            modal.beforeClose()
//            expect(modal.steps[0].view.close).toHaveBeenCalled()
//            expect(modal.steps[1].view.close).toHaveBeenCalled()
//            expect(modal.steps[2].view.close).toHaveBeenCalled()
//        })
//    })
//
//    describe("view/modal-wizard step1", function () {
//        var app, view
//
//        beforeEach(function () {
//            app = new Application.Spec
//            view = new ModalWizard.Step1({model:app})
//            view.locations.url = "fixtures/location-list.json"
//            view.locations.fetch({async:false})
//            view.render()
//        })
//
//        afterEach(function () {
//            view.close()
//        })
//
//        it("does not validate empty view", function () {
//            expect(view.validate()).toBe(false)
//            expect(view.$("#app-locations ul").html()).toBe("")
//            expect(view.$("#application-name").text()).toBe("")
//        })
//
//        it("updates the name on blur", function () {
//            view.$("#application-name").val("myapp")
//            view.$("#application-name").trigger("blur")
//            expect(app.get("name")).toBe("myapp")
//        })
//
//        it("adds and removes location", function () {
//            expect(app.get("locations").length).toBe(0)
//            view.$("#add-app-location").trigger("click")
//            expect(app.get("locations").length).toBe(1)
//            view.$(".remove").trigger("click")
//            expect(app.get("locations").length).toBe(0)
//        })
//
//    })
//
//    describe("view/modal-wizard step2", function () {
//        var app, view
//
//        beforeEach(function () {
//            app = new Application.Spec
//            view = new ModalWizard.Step2({model:app})
//            // TODO supply catalog entities
//            view.render()
//        })
//
//        afterEach(function () {
//            view.close()
//        })
//
//        // to be added
//    })
//
//    describe("view/modal-wizard step3", function () {
//        var app, view
//
//        beforeEach(function () {
//            app = new Application.Spec
//            view = new ModalWizard.Step3({model:app})
//            view.render()
//        })
//
//        afterEach(function () {
//            view.close()
//        })
//
//        it("has #app-summary to render the application", function () {
//            expect(view.$("#app-summary").length).toBe(1)
//        })
//
//        it("validates only when application spec contains data", function () {
//            expect(view.validate()).toBe(false)
//            app.set({name:"myapp", locations:["/dummy/1"], entities:[
//                {}
//            ]})
//            expect(view.validate()).toBe(true)
//        })
//    })
//
//    describe('tpl/home/step1.html', function () {
//        var $step = $(Step1Html)
//
//        it('must have input#application-name', function () {
//            expect($step.find('input#application-name').length).toEqual(1);
//        });
//
//        it('must have div#app-locations', function () {
//            var $appLocations = $step.filter('div#app-locations');
//            expect($appLocations.length).toEqual(1);
//            expect($appLocations.find('h4').length).toEqual(1);
//            expect($appLocations.find('ul').length).toEqual(1);
//            expect($appLocations.find('button#toggle-selector-container').length).toEqual(1);
//        });
//
//        it('must have select#select-location', function () {
//            expect($step.find('select#select-location').length).toEqual(1);
//        });
//
//        it('must have button#add-app-location', function () {
//            expect($step.find('button#add-app-location').length).toEqual(1);
//        });
//        it('must have div.info-message', function () {
//            expect($step.filter('div.info-message').length).toEqual(1);
//        })
//    });
//
//    describe('tpl/home/step2.html', function () {
//        var $step = $(Step2Html);
//
//        it('must have div#entities with h4 and ul', function () {
//            var $div = $step.filter('div#entities');
//            expect($div.length).toEqual(1);
//            expect($div.find('h4').length).toEqual(1);
//            expect($div.find('ul').length).toEqual(1);
//            expect($div.find('button#toggle-entity-form').length).toEqual(1);
//        });
//
//        it('must have div#new-entity with all components', function () {
//            var $div = $step.filter('div#new-entity');
//            expect($div.length).toEqual(1);
//            expect($div.find('div#entity-form').length).toEqual(1);
//            expect($div.find('button#add-app-entity').length).toEqual(1);
//            expect($div.find('div.entity-info-message').length).toEqual(1);
//        });
//        it('must have div.info-message', function () {
//            expect($step.filter('div.info-message').length).toEqual(1);
//        });
//    });
//
//    describe('tpl/home/step3.html', function () {
//        var $step = $(Step3Html);
//        it('div.body must have h3 and textarea#app-summary', function () {
//            expect($step.find('h3').length).toEqual(1);
//            expect($step.find('textarea#app-summary').length).toEqual(1);
//        });
//        it('must have div.info-message', function () {
//            expect($step.filter('div.info-message').length).toEqual(1);
//        });
//    });
})