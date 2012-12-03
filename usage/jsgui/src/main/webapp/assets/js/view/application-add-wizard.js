/**
 * Builds a Twitter Bootstrap modal as the framework for a Wizard.
 * Also creates an empty Application model.
 */
define([
    "underscore", "jquery", "backbone", "model/entity", "model/application", "formatJson",
    "model/location", "text!tpl/app-add-wizard/modal-wizard.html",
    
    "text!tpl/app-add-wizard/create.html",
    "text!tpl/app-add-wizard/create-step-template-entry.html", 
    "text!tpl/app-add-wizard/create-entity-entry.html", 
    "text!tpl/app-add-wizard/edit-config-entry.html",
    
    "text!tpl/app-add-wizard/deploy.html", 
    "text!tpl/app-add-wizard/deploy-location-row.html", "text!tpl/app-add-wizard/deploy-location-option.html",
    
    "text!tpl/app-add-wizard/preview.html",
    
    "bootstrap", "brooklyn-utils"
    
], function (_, $, Backbone, Entity, Application, FormatJSON, Location, ModalHtml, 
		CreateHtml, 
		CreateStepTemplateEntryHtml, CreateEntityEntryHtml, EditConfigEntryHtml,
		DeployHtml, 
		DeployLocationRowHtml, DeployLocationOptionHtml,  
		PreviewHtml
		) {

    var ModalWizard = Backbone.View.extend({
        tagName:'div',
        className:'modal hide fade',
        events:{
            'click #prev_step':'prevStep',
            'click #next_step':'nextStep',
            'click #preview_step':'previewStep',
            'click #finish_step':'finishStep'
        },
        template:_.template(ModalHtml),
        initialize:function () {
            this.model = new Application.Spec
            this.currentStep = 0;
            this.steps = [
                          {
                              step_id:'what-app',
                              title:'Create Application',
                              instructions:'Choose or build the application to deploy',
                              view:new ModalWizard.StepCreate({ model:this.model})
                          },
                          {
                              step_id:'name-and-locations',
                              title:'Deploy Application',
                              instructions:'Specify the locations to deploy to and any additional configuration',
                              view:new ModalWizard.StepDeploy({ model:this.model })
                          },
                          {
                              step_id:'preview',
                              title:'Application Preview',
                              instructions:'Confirm the code which will be sent to the server, optionally tweaking it or saving it for future reference',
                              view:new ModalWizard.StepPreview({ model:this.model})
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

            var currentStep = this.steps[this.currentStep]
            this.title.html(currentStep.title)
            this.instructions.html(currentStep.instructions)
            this.currentView = currentStep.view
            
            // delegate to sub-views !!
            this.$(".modal-body").replaceWith(this.currentView.render().el)

            setVisibility(this.$("#prev_step"), (this.currentStep > 0))
            setVisibility(this.$("#next_step"), (this.currentStep < 1))
            setVisibility(this.$("#preview_step"), (this.currentStep == 1))
            setVisibility(this.$("#finish_step"), (this.currentStep >= 1))
        },
        submitApplication:function (event) {
            var that = this
            var $modal = $('#modal-container.add-app .modal')
            $modal.fadeTo(500,0.5);
            $.ajax({
                url:'/v1/applications',
                type:'post',
                contentType:'application/json',
                processData:false,
                data:JSON.stringify(this.model.toJSON()),
                success:function (data) {
                    $modal.modal('hide')
                    $modal.fadeTo(500,1);
                    if (that.options.callback) that.options.callback();
                },
                error:function (data) {
                    that.$el.fadeTo(100,1).delay(200).fadeTo(200,0.2).delay(200).fadeTo(200,1);
                	that.steps[that.currentStep].view.showFailure()
                }
            })
            return false
        },
        prevStep:function () {
            this.currentStep -= 1
            this.renderCurrentStep()
        },
        nextStep:function () {
            if (this.currentStep < 2) {
                if (this.currentView.validate()) {
                    this.currentStep += 1
                    this.renderCurrentStep()
                }
            } else {
                this.finishStep()
            }
        },
        previewStep:function () {
            // slight cheat, but good enough for now
            this.nextStep()
        },
        finishStep:function () {
            if (this.currentView.validate()) {
                this.submitApplication()
            }
        }
    })
    
        // Note: this does not restore values on a back click; setting type and entity type+name is easy,
    // but relevant config lines is a little bit more tedious
    ModalWizard.StepCreate = Backbone.View.extend({
        className:'modal-body',
        events:{
            'click #add-app-entity':'addEntityBox',
            'click .editable-entity-heading':'expandEntity',
            'click .remove-entity-button':'removeEntityClick',
            'click .editable-entity-button':'saveEntityClick',
            'click #remove-config':'removeConfigRow',
            'click #add-config':'addConfigRow',
            'click .template-lozenge':'templateClick',
            'change .text-filter input':'applyFilter',
            'keyup .text-filter input':'applyFilter',
            'shown a[data-toggle="tab"]':'onTabChange'
        },
        template:_.template(CreateHtml),
        initialize:function () {
            var self = this
            self.catalogEntityIds = []
            self.catalogApplicationIds = []
            
            this.$el.html(this.template({}))
            
            this.addEntityBox()
            
            $.get('/v1/catalog/entities', {}, function (result) {
                self.catalogEntityItems = result
                self.catalogEntityIds = _.map(result, function(item) { return item.id })
                self.$(".entity-type-input").typeahead().data('typeahead').source = self.catalogEntityIds
            })
            $.get('/v1/catalog/applications', {}, function (result) {
                self.catalogApplicationItems = result
                self.catalogApplicationIds = _.map(result, function(item) { return item.id })
                self.$("#appClassTab .application-type-input").typeahead().data('typeahead').source = self.catalogApplicationIds
                self.addTemplateLozenges()
            })
        },
        beforeClose:function () {
        },
        renderConfiguredEntities:function () {
            var $configuredEntities = this.$('#entitiesAccordionish').empty()
            var that = this
            if (this.model.get("entities") && this.model.get("entities").length > 0) {
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
        onTabChange: function(e) {
            if (e.target.text=="Template")
                $("li.text-filter").show()
            else
                $("li.text-filter").hide()
        },
        applyFilter: function(e) {
            var filter = $(e.currentTarget).val().toLowerCase()
            if (!filter) {
                $(".template-lozenge").show()
            } else {
                _.each($(".template-lozenge"), function(it) {
                    var viz = $(it).text().toLowerCase().indexOf(filter)>=0
                    if (viz) 
                        $(it).show() 
                    else 
                        $(it).hide()
                })
            }
        },
        addTemplateLozenges: function(event) {
            var that = this
            _.each(this.catalogApplicationItems, function(item) {
                that.addTemplateLozenge(that, item)
            })
        },
        addTemplateLozenge: function(that, item) {
            var $tempel = _.template(CreateStepTemplateEntryHtml, {
                id: item.id,
                name: item.name,
                description: item.description,
                iconUrl: item.iconUrl
            })
            $("#create-step-template-entries", that.$el).append($tempel)
        },
        templateClick: function(event) {
            var $tl = $(event.target).closest(".template-lozenge");
            var wasSelected = $tl.hasClass("selected")
            $(".template-lozenge").removeClass("selected")
            if (!wasSelected) {
                $tl.addClass("selected")
                this.selectedTemplateId = $tl.attr('id');
            } else {
                this.selectedTemplateId = null;
            }
        },
        expandEntity:function (event) {
            $(event.currentTarget).next().show('fast').delay(1000).prev().hide('slow')
        },
        saveEntityClick:function (event) {
            this.saveEntity($(event.currentTarget).closest(".editable-entity-group"));
        },
        saveEntity:function ($entityGroup) {
            var that = this
            var name = $('#entity-name',$entityGroup).val()
            var type = $('#entity-type',$entityGroup).val()
            if (type=="" || !_.contains(that.catalogEntityIds, type)) {
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
            $('.app-add-wizard-config-entry',root).each( function (index,elt) {
                map[$('#key',elt).val()] = $('#value',elt).val()
            })
            return map;
        },
        saveTemplate:function () {
            var type = this.selectedTemplateId
            if (type === undefined) return false
            if (!_.contains(this.catalogApplicationIds, type)) {
                $('.entity-info-message').show('slow').delay(2000).hide('slow')
                return false
            }
            this.model.set("type", type);
            return true;
        },
        saveAppClass:function () {
            var that = this
            var tab = $.find('#appClassTab')
            var type = $(tab).find('#app-java-type').val()
            if (!_.contains(this.catalogApplicationIds, type)) {
                $('.entity-info-message').show('slow').delay(2000).hide('slow')
                return false
            }
            this.model.set("type", type);
            return true;
        },
        addEntityBox:function () {
            var entity = new Entity.Model
            this.model.addEntity( entity )
            this.addEntityHtml($('#entitiesAccordionish', this.$el), entity)
        },
        addEntityHtml:function (parent, entity) {
            var $entity = _.template(CreateEntityEntryHtml, {})
            var that = this
            parent.append($entity)
            parent.children().last().find('.entity-type-input').typeahead({ source: that.catalogEntityIds })
        },        
        removeEntityClick:function (event) {
            var $entityGroup = $(event.currentTarget).parent().parent().parent();
            this.model.removeEntityIndex($entityGroup.index())
            $entityGroup.remove()
        },
        
        addConfigRow:function (event) {
            var $row = _.template(EditConfigEntryHtml, {})
            $(event.currentTarget).parent().prev().append($row)
        },
        removeConfigRow:function (event) {
            $(event.currentTarget).parent().remove()
        },
        
        validate:function () {
            var that = this
            var tabName = $('#app-add-wizard-create-tab li[class="active"] a').attr('href')
            if (tabName=='#entitiesTab') {
                var allokay = true
                $($.find('.editable-entity-group')).each(
                    function (i,$entityGroup) {
                        allokay = that.saveEntity($($entityGroup)) & allokay
                    })
                if (!allokay) return false;
                if (this.model.get("entities") && this.model.get("entities").length > 0) {
                    this.model.set("type", null);
                    return true;
                }
            } else if (tabName=='#templateTab') {
                if (this.saveTemplate()) {
                    this.model.set("entities", []);
                    return true
                }
            } else if (tabName=='#appClassTab') {
                if (this.saveAppClass()) {
                    this.model.set("entities", []);
                    return true
                }
            } else {
                log("NOT IMPLEMENTED YET")
                // TODO - other tabs not implemented yet 
                // do nothing, show error return false below
            }
            this.$('div.app-add-wizard-create-info-message').show('slow').delay(2000).hide('slow')
            return false
        }

    })

    ModalWizard.StepDeploy = Backbone.View.extend({
        className:'modal-body',
        events:{
            'click #add-selector-container':'addLocation',
            'click #remove-app-location':'removeLocation',
            'change select':'selection',
            'change option':'selection',
            'blur #application-name':'updateName',
            'click #remove-config':'removeConfigRow',
            'click #add-config':'addConfigRow'
        },
        template:_.template(DeployHtml),
        locationRowTemplate:_.template(DeployLocationRowHtml),
        locationOptionTemplate:_.template(DeployLocationOptionHtml),

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
        addConfigRow:function (event) {
            var $row = _.template(EditConfigEntryHtml, {})
            $(event.currentTarget).parent().prev().append($row)
        },
        removeConfigRow:function (event) {
            $(event.currentTarget).parent().remove()
        },
        getConfigMap:function() {
            var map = {}
            $('.app-add-wizard-config-entry').each( function (index,elt) {
                map[$('#key',elt).val()] = $('#value',elt).val()
            })
            return map;
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
            var name = this.$('#application-name').val()
            if (name)
                this.model.set("name", name)
            else
                this.model.set("name", "")
        },
        validate:function () {
            this.model.set("config", this.getConfigMap())
            if (this.model.get("locations").length !== 0) {
                return true
            }
            this.$('div.info-message').show('slow').delay(2000).hide('slow')
            return false
        }
    })

    ModalWizard.StepPreview = Backbone.View.extend({
        className:'modal-body',
        initialize:function () {
            this.$el.html(_.template(PreviewHtml))
            this.model.on("change", this.render, this)
        },
        beforeClose:function () {
            this.model.off("change", this.render)
        },
        render:function () {
            if (!this.model.get("entities") || this.model.get("entities").length==0) {
                delete this.model.attributes["entities"]
            }
            if (!this.model.get("name"))
                delete this.model.attributes["name"]
            if (!this.model.get("config") || _.keys(this.model.get("config")).length==0) {
                delete this.model.attributes["config"]
            }

            this.$('#app-summary').val(FormatJSON(this.model.toJSON()))
            this.delegateEvents()
            return this
        },
        validate:function () {
            // need locations, and type or entities
            if ((this.model.get("locations").length > 0) && 
                (this.model.get("type")!=null || 
            		this.model.getEntities().length > 0)) {
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
