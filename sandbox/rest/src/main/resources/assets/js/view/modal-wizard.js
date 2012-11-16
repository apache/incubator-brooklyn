/**
 * Builds a Twitter Bootstrap modal as the framework for a Wizard.
 * Also creates an empty Application model.
 */
define([
    "underscore", "jquery", "backbone", "model/entity", "model/application", "formatJson",
    "model/location", "text!tpl/home/modal-wizard.html", 
    "text!tpl/home/step1.html", "text!tpl/home/step2.html", "text!tpl/home/step3.html", 
    "text!tpl/home/step1-location-row.html", "text!tpl/home/step1-location-option.html",
    "text!tpl/home/step2-entity-entry.html", "text!tpl/home/step2-config-entry.html", "bootstrap"
], function (_, $, Backbone, Entity, Application, FormatJSON, Location, ModalHtml, 
		Step1Html, Step2Html, Step3Html, 
		Step1LocationRowHtml, LocationOptionHtml,  
		Step2EntityEntryHtml, Step2ConfigEntryHtml) {

    var ModalWizard = Backbone.View.extend({
        tagName:'div',
        className:'modal hide fade',
        events:{
            'click #next_step':'nextStep',
            'click #prev_step':'prevStep'
        },
        template:_.template(ModalHtml),
        initialize:function () {
            this.model = new Application.Spec
            this.currentStep = 0
            this.steps = [
                {
                    step_number:1,
                    title:'Deploy Application',
                    instructions:'Enter the name of the new application and the location(s) where you wish to deploy it.',
                    view:new ModalWizard.Step1({ model:this.model })
                },
                {
                    step_number:2,
                    title:'Configure Application',
                    instructions:'Define how the application is built and the configuration parameters',
                    view:new ModalWizard.Step2({ model:this.model})
                },
                {
                    step_number:3,
                    title:'Application Summary',
                    instructions:'Confirm and save the JSON details which will be used to create the application',
                    view:new ModalWizard.Step3({ model:this.model})
                }
            ]
        },
        beforeClose:function () {
            // ensure we close the sub-views
            _.each(this.steps, function (step) {
                step.view.close()
            }, this)
        },
        render:function () {
            this.$el.html(this.template({}))
            this.renderCurrentStep()
            return this
        },

        renderCurrentStep:function () {
            this.title = this.$("h3#step_title")
            this.instructions = this.$("p#step_instructions")
            this.nextStepButton = this.$("#next_step")
            this.prevStepButton = this.$("#prev_step")

            var currentStep = this.steps[this.currentStep]
            if (!this.isFirstStep()) var prevStep = this.steps[this.currentStep - 1]
            var nextStep = this.steps[this.currentStep + 1]

            this.title.html(currentStep.title)
            this.instructions.html(currentStep.instructions)
            this.currentView = currentStep.view
            // delegate to sub-views !!
            this.$(".modal-body").replaceWith(this.currentView.render().el)

            if (prevStep) {
                this.prevStepButton.html("Previous").show()
            } else {
                this.prevStepButton.hide()
            }
            if (nextStep) {
                this.nextStepButton.html("Next")
            } else {
                this.nextStepButton.html("Finish")
            }
        },
        submitApplication:function (event) {
            var that = this
            $.ajax({
                url:'/v1/applications',
                type:'post',
                contentType:'application/json',
                processData:false,
                data:JSON.stringify(this.model.toJSON()),
                success:function (data) {
                    var $modal = $('#modal-container .modal')
                    $modal.modal('hide')
                    if (that.options.callback) that.options.callback();
                },
                error:function (data) {
                	that.steps[that.currentStep].view.showFailure()
                }
            })
            return false
        },
        nextStep:function () {
            if (this.currentView.validate()) {
                if (!this.isLastStep()) {
                    this.currentStep += 1
                    this.renderCurrentStep()
                } else {
                    this.submitApplication()
                }
            }
        },
        prevStep:function () {
            if (!this.isFirstStep()) {
                this.currentStep -= 1
                this.renderCurrentStep()
            }
        },
        isFirstStep:function () {
            return (this.currentStep == 0)
        },
        isLastStep:function () {
            return (this.currentStep == this.steps.length - 1)
        }
    })

    /**
     * Wizard for creating a new application. First step: assign a name and a location for the app.
     */
    ModalWizard.Step1 = Backbone.View.extend({
        className:'modal-body',
        events:{
            'click #add-selector-container':'addLocation',
            'click #remove-app-location':'removeLocation',
            'change select':'selection',
            'change option':'selection',
            'blur #application-name':'updateName'
        },
        template:_.template(Step1Html),
        locationRowTemplate:_.template(Step1LocationRowHtml),
        locationOptionTemplate:_.template(LocationOptionHtml),

        initialize:function () {
            this.model.on("change", this.render, this)
            this.$el.html(this.template({}))
            this.locations = new Location.Collection()
        },
        beforeClose:function () {
            this.model.off("change", this.render)
        },
        renderName:function () {
            this.$('#application-name').val(this.model.get("name"))
        },
        renderAddedLocations:function () {
            // renders the locations added to the model
        	var that = this;
        	var container = this.$("#selector-container")
        	container.empty()
        	for (var li = 0; li < this.model.get("locations").length; li++) {
        		var chosenLocation = this.model.get("locations")[li];
        		container.append(that.locationRowTemplate({
        				initialValue: chosenLocation,
        				rowId: li
        			}))
        	}
    		var $selectLocations = container.find('#select-location')
    		this.locations.each(function(aLocation) {
        			var $option = that.locationOptionTemplate({
                        url:aLocation.getLinkByName("self"),
                        name:aLocation.getPrettyName()
                    })
                    $selectLocations.append($option)
        		})
    		$selectLocations.each(function(i) {
    			var url = $($selectLocations[i]).parent().attr('initialValue');
    			$($selectLocations[i]).val(url)
    		})
        },
        render:function () {
        	var that = this
            this.renderName()
            this.locations.fetch({async:false,
                success:function () {
                	if (that.model.get("locations").length==0)
                		that.addLocation()
            		else
            			that.renderAddedLocations()
                }})
            this.delegateEvents()
            return this
        },
        addLocation:function () {
        	if (this.locations.models.length>0) {
            	this.model.addLocation(this.locations.models[0].getLinkByName("self"))
            	this.renderAddedLocations()
        	} else {
                this.$('div.info-nolocs-message').show('slow').delay(2000).hide('slow')
        	}
        },
        removeLocation:function (event) {
            var toBeRemoved = $(event.currentTarget).parent().attr('rowId')
            this.model.removeLocationIndex(toBeRemoved)
            this.renderAddedLocations()
        },
        selection:function (event) {
        	var url = $(event.currentTarget).val();
        	var loc = this.locations.find(function (candidate) {
        		return candidate.getLinkByName("self")==url
    		})
        	this.model.setLocationAtIndex($(event.currentTarget).parent().attr('rowId'), 
        			loc.getLinkByName("self"))
        },
        updateName:function () {
            this.model.set("name", this.$('#application-name').val())
        },
        validate:function () {
            if (this.model.get("name") !== "" && this.model.get("locations").length !== 0) {
                return true
            }
            this.$('div.info-message').show('slow').delay(2000).hide('slow')
            return false
        }
    })

    /**
     * Second step from the create application wizard. Allows you to add and new entities and configure them.
     */
    // Note: this does not restore values on a back click; setting type and entity type+name is easy,
    // but relevant config lines is a little bit more tedious
    ModalWizard.Step2 = Backbone.View.extend({
        className:'modal-body',
        events:{
            'click #add-app-entity':'addEntity',
            'click .editable-entity-heading':'expandEntity',
            'click .remove-entity-button':'removeEntityClick',
            'click .editable-entity-button':'saveEntityClick',
            'click #remove-config':'removeConfigRow',
            'click #add-config':'addConfigRow'
        },
        template:_.template(Step2Html),
        initialize:function () {
            var self = this
            self.catalogEntities = []
            self.catalogApplications = []
            
            this.$el.html(this.template({}))
            this.addEntity()
            
            $.get('/v1/catalog/entities', {}, function (result) {
                self.catalogEntities = result
                self.$(".entity-type-input").typeahead().data('typeahead').source = self.catalogEntities
            })
            $.get('/v1/catalog/applications', {}, function (result) {
                self.catalogApplications = result
                self.$(".application-type-input").typeahead().data('typeahead').source = self.catalogApplications
            })
        },
        beforeClose:function () {
        },
        renderConfiguredEntities:function () {
            var $configuredEntities = this.$('#entitiesAccordionish').empty()
            var that = this
            if (this.model.get("entities").length > 0) {
                _.each(this.model.get("entities"), function (entity) {
                	that.addEntityHtml($configuredEntities, entity)
                })
            }
        },
        
        render:function () {
            this.renderConfiguredEntities()
            this.delegateEvents()
            return this
        },
        
        expandEntity:function (event) {
        	$(event.currentTarget).next().show('fast').delay(1000).prev().hide('slow')
        },
        saveEntityClick:function (event) {
        	this.saveEntity($(event.currentTarget).parent().parent().parent());
        },
        saveEntity:function ($entityGroup) {
        	var that = this
        	var name = $('#entity-name',$entityGroup).val()
        	var type = $('#entity-type',$entityGroup).val()
        	if (type=="" || !_.contains(that.catalogEntities, type)) {
        		$('.entity-info-message',$entityGroup).show('slow').delay(2000).hide('slow')
        		return false
        	}
    		var saveTarget = this.model.get("entities")[$entityGroup.index()];
    		this.model.set("type", null)
    		saveTarget.name = name
    		saveTarget.type = type
    		saveTarget.config = this.getConfigMap($entityGroup)
    		
    		if (name=="") name=type;
    		if (name=="") name="<i>(new entity)</i>";
    		$('#entity-name-header',$entityGroup).html( name )
    		$('.editable-entity-body',$entityGroup).prev().show('fast').next().hide('fast')
        	return true;
        },
        getConfigMap:function (root) {
        	var map = {}
        	$('.step2-entity-config',root).each( function (index,elt) {
        		map[$('#key',elt).val()] = $('#value',elt).val()
        	})
        	return map;
        },
        saveTemplate:function () {
        	var that = this
        	var tab = $.find('#templateTab')
        	var type = $(tab).find('#entity-type').val()
        	if (!_.contains(this.catalogApplications, type)) {
        		$('.entity-info-message').show('slow').delay(2000).hide('slow')
        		return false
        	}
    		this.model.set("type", type);
    		this.model.set("config", this.getConfigMap(tab))
        	return true;
        },
        addEntity:function () {
        	var entity = new Entity.Model
        	this.model.addEntity( entity )
        	this.addEntityHtml(this.$('#entitiesAccordionish'), entity)
        },
    	addEntityHtml:function (parent, entity) {
            var $entity = _.template(Step2EntityEntryHtml, {})
            var that = this
            parent.append($entity)
            parent.children().last().find('.entity-type-input').typeahead({ source: that.catalogEntities })
        },        
        removeEntityClick:function (event) {
        	var $entityGroup = $(event.currentTarget).parent().parent().parent();
        	this.model.removeEntityIndex($entityGroup.index())
        	$entityGroup.remove()
        },
        
        addConfigRow:function (event) {
        	var $row = _.template(Step2ConfigEntryHtml, {})
        	$(event.currentTarget).parent().prev().append($row)
        },
        removeConfigRow:function (event) {
        	$(event.currentTarget).parent().remove()
        },
        
        validate:function () {
        	var that = this
        	var tabName = $('#step2Tab li[class="active"] a').attr('href')
        	if (tabName=='#entitiesTab') {
        		var allokay = true
        		$($.find('.editable-entity-group')).each(
    				function (i,$entityGroup) {
    					allokay = that.saveEntity($entityGroup) & allokay
    				})
				if (!allokay) return false;
        		if (this.model.get("entities").length > 0) {
        			this.model.set("type", null);
        			return true;
        		}
        	} else if (tabName=='#templateTab') {
        		if (this.saveTemplate()) {
        			this.model.set("entities", []);
        			return true
        		}
            } else {
            	// other tabs not implemented yet 
            	// do nothing, show error return false below
            }
            this.$('div.step2-info-message').show('slow').delay(2000).hide('slow')
            return false
        }

    })
    /**
     * Final step from the create application wizard. Review the summary and submit the request.
     */
    ModalWizard.Step3 = Backbone.View.extend({
        className:'modal-body',
        initialize:function () {
            this.$el.html(_.template(Step3Html))
            this.model.on("change", this.render, this)
        },
        beforeClose:function () {
            this.model.off("change", this.render)
        },
        render:function () {
            this.$('#app-summary').val(FormatJSON(this.model.toJSON()))
            this.delegateEvents()
            return this
        },
        validate:function () {
            if (this.model.get("name") != ""
                && this.model.get("locations").length > 0
                && (this.model.get("type")!=null || 
                		this.model.get("entities").length > 0)) {
                return true
            }
            this.showFailure()
            return false
        },
        showFailure:function () {
        	this.$('div.info-message').show('slow').delay(2000).hide('slow')
        }
    })

    return ModalWizard
})