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
/**
 * Builds a Twitter Bootstrap modal as the framework for a Wizard.
 * Also creates an empty Application model.
 */
define([
    "underscore", "jquery", "backbone", "brooklyn-utils", "js-yaml",
    "model/entity", "model/application", "model/location",
    "text!tpl/app-add-wizard/modal-wizard.html",
    "text!tpl/app-add-wizard/create.html",
    "text!tpl/app-add-wizard/create-step-template-entry.html", 
    "text!tpl/app-add-wizard/create-entity-entry.html", 
    "text!tpl/app-add-wizard/required-config-entry.html",
    "text!tpl/app-add-wizard/edit-config-entry.html",
    "text!tpl/app-add-wizard/deploy.html",
    "text!tpl/app-add-wizard/deploy-location-row.html",
    "text!tpl/app-add-wizard/deploy-location-option.html",
    "text!tpl/app-add-wizard/preview.html",
    "bootstrap"
    
], function (_, $, Backbone, Util, JsYaml, Entity, Application, Location,
             ModalHtml, CreateHtml, CreateStepTemplateEntryHtml, CreateEntityEntryHtml,
             RequiredConfigEntryHtml, EditConfigEntryHtml, DeployHtml,
             DeployLocationRowHtml, DeployLocationOptionHtml, PreviewHtml
) {

    /** Special ID to indicate that no locations will be provided when starting the server. */
    var NO_LOCATION_INDICATOR = "__NONE__";

    function setVisibility(obj, isVisible) {
        if (isVisible) obj.show();
        else obj.hide();
    }

    function setEnablement(obj, isEnabled) {
        obj.attr("disabled", !isEnabled)
    }
    
    function specToCAMP(spec) {
        var services;
        if (spec.type) {
            services = [entityToCAMP(spec)];
        } else if (spec.entities) {
            services = [];
            var entities = spec.entities;
            for (var i = 0; i < entities.length; i++) {
                services.push(entityToCAMP(entities[i]));
            }
        }
        return {
            name: spec.name,
            locations: spec.locations,
            services: services
        };
    }

    function entityToCAMP(entity) {
        return {
            name: entity.name,
            type: entity.type,
            "brooklyn.config": entity.config
        };
    }
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
            this.model = {}
            this.model.spec = new Application.Spec;
            this.model.yaml = "";
            this.model.mode = "template";  // or "yaml" or "other"
            this.currentStep = 0;
            this.steps = [
                          {
                              step_id:'what-app',
                              title:'Create Application',
                              instructions:'Choose or build the application to deploy',
                              view:new ModalWizard.StepCreate({ model:this.model, wizard: this })
                          },
                          {
                              step_id:'name-and-locations',
                              title:'<%= appName %>',
                              instructions:'Specify the locations to deploy to and any additional configuration',
                              view:new ModalWizard.StepDeploy({ model:this.model })
                          },
                          {
                              step_id:'preview',
                              title:'<%= appName %>',
                              instructions:'Confirm the code which will be sent to the server, optionally tweaking it or saving it for future reference',
                              view:new ModalWizard.StepPreview({ model:this.model })
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
            var name = this.model.name || "";
            this.title = this.$("h3#step_title")
            this.instructions = this.$("p#step_instructions")

            var currentStepObj = this.steps[this.currentStep]
            this.title.html(_.template(currentStepObj.title)({appName: name}));
            this.instructions.html(currentStepObj.instructions)
            this.currentView = currentStepObj.view
            
            // delegate to sub-views !!
            this.currentView.render()
            this.currentView.updateForState()
            this.$(".modal-body").replaceWith(this.currentView.el)

            this.updateButtonVisibility();
        },
        updateButtonVisibility:function () {
            var currentStepObj = this.steps[this.currentStep]
            
            setVisibility(this.$("#prev_step"), (this.currentStep > 0))

            // next shown for first step, but not for yaml
            var nextVisible = (this.currentStep < 1) && (this.model.mode != "yaml")
            setVisibility(this.$("#next_step"), nextVisible)
            
            // previous shown for step 2 (but again, not yaml)
            var previewVisible = (this.currentStep == 1) && (this.model.mode != "yaml")
            setVisibility(this.$("#preview_step"), previewVisible)
            
            // now set next/preview enablement
            if (nextVisible || previewVisible) {
                var nextEnabled = true;
                if (this.currentStep==0 && this.model.mode=="template" && currentStepObj && currentStepObj.view) {
                    // disable if this is template selction (lozenge) view, and nothing is selected
                    if (! currentStepObj.view.selectedTemplate)
                        nextEnabled = false;
                }
                
                if (nextVisible)
                    setEnablement(this.$("#next_step"), nextEnabled)
                if (previewVisible)
                    setEnablement(this.$("#preview_step"), nextEnabled)
            }
            
            // finish from config step, preview step, and from first step if yaml tab selected (and valid)
            var finishVisible = (this.currentStep >= 1)
            var finishEnabled = finishVisible
            if (!finishEnabled && this.currentStep==0) {
                if (this.model.mode == "yaml") {
                    // should do better validation than non-empty
                    finishVisible = true;
                    var yaml_code = this.$("#yaml_code").val()
                    if (yaml_code) {
                        finishEnabled = true;
                    }
                }
            }
            setVisibility(this.$("#finish_step"), finishVisible)
            setEnablement(this.$("#finish_step"), finishEnabled)
        },
        
        submitApplication:function (event) {
            var that = this
            var $modal = $('.add-app #modal-container .modal')
            $modal.fadeTo(500,0.5);
            
            var yaml;
            if (this.model.mode == "yaml") {
                yaml = this.model.yaml;
            } else {
                // Drop any "None" locations.
                this.model.spec.pruneLocations();
                yaml = JSON.stringify(specToCAMP(this.model.spec.toJSON()));
            }

            $.ajax({
                url:'/v1/applications',
                type:'post',
                contentType:'application/yaml',
                processData:false,
                data:yaml,
                success:function (data) {
                    that.onSubmissionComplete(true, data, $modal)
                },
                error:function (data) {
                    that.onSubmissionComplete(false, data, $modal)
                }
            });

            return false
        },
        onSubmissionComplete: function(succeeded, data, $modal) {
            var that = this;
            if (succeeded) {
                $modal.modal('hide')
                $modal.fadeTo(500,1);
                if (that.options.callback) that.options.callback();             
            } else {
                log("ERROR submitting application: "+data.responseText);
                var response, summary="Server responded with an error";
                try {
                    if (data.responseText) {
                        response = JSON.parse(data.responseText)
                        if (response) {
                            summary = response.message;
                        } 
                    }
                } catch (e) {
                    summary = data.responseText;
                }
                that.$el.fadeTo(100,1).delay(200).fadeTo(200,0.2).delay(200).fadeTo(200,1);
                that.steps[that.currentStep].view.showFailure(summary)
            }
        },

        prevStep:function () {
            this.currentStep -= 1
            this.renderCurrentStep()
        },
        nextStep:function () {
            if (this.currentStep < 2) {
                if (this.currentView.validate()) {
                    var yaml = (this.currentView && this.currentView.selectedTemplate && this.currentView.selectedTemplate.yaml);
                    if (yaml) {
                        try {
                            yaml = JsYaml.safeLoad(yaml);
                            yaml = (yaml.location || yaml.locations ? true : false);
                        } catch (e) {
                            yaml = false;
                        }
                    }
                    if (yaml) {
                        // it's a yaml catalog template which includes a location, show the yaml tab
           	            $("ul#app-add-wizard-create-tab").find("a[href='#yamlTab']").tab('show');
                        $("#yaml_code").focus()
                    } else {
                        // it's a java catalog template or yaml template without a location, go to wizard
                        this.currentStep += 1
                        this.renderCurrentStep()
                    }
                } else {
                    // the call to validate will have done the showFailure
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
            } else {
                // call to validate should showFailure
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
            'keyup .text-filter input':'applyFilter',
            'change .text-filter input':'applyFilter',
            'paste .text-filter input':'applyFilter',
            'keyup #yaml_code':'onYamlCodeChange',
            'change #yaml_code':'onYamlCodeChange',
            'paste #yaml_code':'onYamlCodeChange',
            'shown a[data-toggle="tab"]':'onTabChange',
            'click #templateTab #catalog-add':'switchToCatalogAdd',
            'click #templateTab #catalog-yaml':'showYamlTab'
        },
        template:_.template(CreateHtml),
        wizard: null,
        initialize:function () {
            var self = this
            self.catalogEntityIds = []
            self.catalogApplicationIds = []

            this.$el.html(this.template({}))

            // for building from entities
            this.addEntityBox()

            // TODO: Make into models, allow options to override, then pass in in test
            // with overrridden url. Can then think about fixing tests in application-add-wizard-spec.js.
            $.get('/v1/catalog/entities', {}, function (result) {
                self.catalogEntityItems = result
                self.catalogEntityIds = _.map(result, function(item) { return item.id })
                self.$(".entity-type-input").typeahead().data('typeahead').source = self.catalogEntityIds
            })
            // TODO use catalog-item-summary.js instead of raw json; see comments in that file
            $.get('/v1/catalog/applications', {}, function (result) {
                self.catalogApplicationItems = result
                self.catalogApplicationIds = _.map(result, function(item) { return item.id })
                self.$("#appClassTab .application-type-input").typeahead().data('typeahead').source = self.catalogApplicationIds
                $('#catalog-applications-throbber').hide();
                $('#catalog-applications-empty').hide();
                if (self.catalogApplicationItems && self.catalogApplicationItems.length > 0) {
                    self.addTemplateLozenges()
                } else {
                    $('#catalog-applications-empty').show();
                    self.showYamlTab();
                }
            })
        },
        renderConfiguredEntities:function () {
            var $configuredEntities = this.$('#entitiesAccordionish').empty()
            var that = this
            if (this.model.spec.get("entities") && this.model.spec.get("entities").length > 0) {
                _.each(this.model.spec.get("entities"), function (entity) {
                    that.addEntityHtml($configuredEntities, entity)
                })
            }
        },
        updateForState: function () {},
        render:function () {
            this.renderConfiguredEntities()
            this.delegateEvents()
            return this
        },
        onTabChange: function(e) {
            var tabText = $(e.target).text();
            if (tabText=="Catalog") {
                $("li.text-filter").show()
            } else {
                $("li.text-filter").hide()
            }

            if (tabText=="YAML") {
                this.model.mode = "yaml";
            } else if (tabText=="Template") {
                this.model.mode = "template";
            } else {
                this.model.mode = "other";
            }

            if (this.options.wizard)
                this.options.wizard.updateButtonVisibility();
        },
        onYamlCodeChange: function() {
            if (this.options.wizard)
                this.options.wizard.updateButtonVisibility();
        },
        switchToCatalogAdd: function() {
            var $modal = $('.add-app #modal-container .modal')
            $modal.modal('hide');
            window.location.href="#v1/catalog/new";
        },
        showYamlTab: function() {
            $("ul#app-add-wizard-create-tab").find("a[href='#yamlTab']").tab('show')
            $("#yaml_code").focus();
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
                name: item.name || item.id,
                description: item.description,
                planYaml:  item.planYaml,
                iconUrl: item.iconUr
            })
            $("#create-step-template-entries", that.$el).append($tempel)
        },
        templateClick: function(event) {
            var $tl = $(event.target).closest(".template-lozenge");
            var wasSelected = $tl.hasClass("selected")
            $(".template-lozenge").removeClass("selected")
            if (!wasSelected) {
                $tl.addClass("selected")
                this.selectedTemplate = {
                    type: $tl.attr('id'),
                    name: $tl.data("name"),
                    yaml: $tl.data("yaml")
                };
                if (this.selectedTemplate.yaml) {
                    $("textarea#yaml_code").val(this.selectedTemplate.yaml);
                } else {
                    $("textarea#yaml_code").val("services:\n- type: "+this.selectedTemplate.type);
                }
            } else {
                this.selectedTemplate = null;
            }

            if (this.options.wizard)
                this.options.wizard.updateButtonVisibility();
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
                that.showFailure("Missing or invalid type");
                return false
            }
            var saveTarget = this.model.spec.get("entities")[$entityGroup.index()];
            this.model.spec.set("type", null)
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
            if (!this.selectedTemplate) return false
            var type = this.selectedTemplate.type;
            if (!_.contains(this.catalogApplicationIds, type)) {
                $('.entity-info-message').show('slow').delay(2000).hide('slow')
                return false
            }
            this.model.spec.set("type", type);
            this.model.name = this.selectedTemplate.name;
            this.model.catalogEntityData = "LOAD"
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
            this.model.spec.set("type", type);
            return true;
        },
        addEntityBox:function () {
            var entity = new Entity.Model
            this.model.spec.addEntity( entity )
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
            this.model.spec.removeEntityIndex($entityGroup.index())
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
                delete this.model.spec.attributes["id"]
                var allokay = true
                $($.find('.editable-entity-group')).each(
                    function (i,$entityGroup) {
                        allokay = that.saveEntity($($entityGroup)) & allokay
                    })
                if (!allokay) return false;
                if (this.model.spec.get("entities") && this.model.spec.get("entities").length > 0) {
                    this.model.spec.set("type", null);
                    return true;
                }
            } else if (tabName=='#templateTab') {
                delete this.model.spec.attributes["id"]
                if (this.saveTemplate()) {
                    this.model.spec.set("entities", []);
                    return true
                }
            } else if (tabName=='#appClassTab') {
                delete this.model.spec.attributes["id"]
                if (this.saveAppClass()) {
                    this.model.spec.set("entities", []);
                    return true
                }
            } else if (tabName=='#yamlTab') {
                this.model.yaml = this.$("#yaml_code").val();
                if (this.model.yaml) {
                    return true;
                }
            } else {
                console.info("NOT IMPLEMENTED YET")
                // TODO - other tabs not implemented yet 
                // do nothing, show error return false below
            }
            this.$('div.app-add-wizard-create-info-message').slideDown(250).delay(10000).slideUp(500)
            return false
        },

        showFailure: function(text) {
            if (!text) text = "Failure performing the specified action";
            this.$('div.error-message .error-message-text').html(_.escape(text));
            this.$('div.error-message').slideDown(250).delay(10000).slideUp(500);
        }

    })

    ModalWizard.StepDeploy = Backbone.View.extend({
        className:'modal-body',

        events:{
            'click #add-selector-container':'addLocation',
            'click #remove-app-location':'removeLocation',
            'change .select-location': 'selection',
            'blur #application-name':'updateName',
            'click #remove-config':'removeConfigRow',
            'click #add-config':'addConfigRow'
        },

        template:_.template(DeployHtml),
        locationRowTemplate:_.template(DeployLocationRowHtml),
        locationOptionTemplate:_.template(DeployLocationOptionHtml),

        initialize:function () {
            this.model.spec.on("change", this.render, this)
            this.$el.html(this.template())
            this.locations = new Location.Collection()
        },
        beforeClose:function () {
            this.model.spec.off("change", this.render)
        },
        renderName:function () {
            this.$('#application-name').val(this.model.spec.get("name"))
        },
        renderAddedLocations:function () {
            // renders the locations added to the model
            var rowTemplate = this.locationRowTemplate,
                optionTemplate = this.locationOptionTemplate,
                container = this.$("#selector-container");
            container.empty();
            for (var li = 0; li < this.model.spec.get("locations").length; li++) {
                var chosenLocation = this.model.spec.get("locations")[li];
                container.append(rowTemplate({
                    initialValue: chosenLocation,
                    rowId: li
                }));
            }
            var $locationOptions = container.find('.select-location');
            var templated = this.locations.map(function(aLocation) {
                return optionTemplate({
                    id: aLocation.id || "",
                    name: aLocation.getPrettyName()
                });
            });

            // insert "none" location
            $locationOptions.append(templated.join(""));
            $locationOptions.each(function(i) {
                var option = $($locationOptions[i]);
                option.val(option.parent().attr('initialValue'));
                // Only append dashes if there are any locations
                if (option.find("option").length > 0) {
                    option.append("<option disabled>------</option>");
                }
                option.append(optionTemplate({
                    id: NO_LOCATION_INDICATOR,
                    name: "None"
                }));
            });
        },
        render:function () {
            this.delegateEvents()
            return this
        },
        updateForState: function () {
            var that = this
            // clear any error message (we are being displayed fresh; if there are errors in the update, we'll show them in code below)
            this.$('div.error-message').hide();
            this.renderName()
            this.locations.fetch({
                success:function () {
                    if (that.model.spec.get("locations").length==0)
                        that.addLocation()
                    else
                        that.renderAddedLocations()
                }})
                
            if (this.model.catalogEntityData==null) {
                this.renderStaticConfig(null)
            } else if (this.model.catalogEntityData=="LOAD") {
                this.renderStaticConfig("LOADING")
                $.get('/v1/catalog/entities/'+this.model.spec.get("type"), {}, function (result) {
                    that.model.catalogEntityData = result
                    that.renderStaticConfig(that.model.catalogEntityData)
                })
            } else {
                this.renderStaticConfig(this.model.catalogEntityData)
            }            
        },
        addLocation:function () {
            if (this.locations.models.length>0) {
                this.model.spec.addLocation(this.locations.models[0].get("id"))
            } else {
                // i.e. No location
                this.model.spec.addLocation(undefined);
            }
            this.renderAddedLocations()
        },
        removeLocation:function (event) {
            var toBeRemoved = $(event.currentTarget).parent().attr('rowId')
            this.model.spec.removeLocationIndex(toBeRemoved)
            this.renderAddedLocations()
        },
        addConfigRow:function (event) {
            var $row = _.template(EditConfigEntryHtml, {})
            $(event.currentTarget).parent().prev().append($row)
        },
        removeConfigRow:function (event) {
            $(event.currentTarget).parent().parent().remove()
        },
        renderStaticConfig:function (catalogEntryItem) {
            this.$('.config-table').html('')
            if (catalogEntryItem=="LOADING") {
                this.$('.required-config-loading').show()
            } else {
                var configs = []
                this.$('.required-config-loading').hide()
                if (catalogEntryItem!=null && catalogEntryItem.config!=null) {
                    var that = this
                    _.each(catalogEntryItem.config, function (cfg) {
                        if (cfg.label) {
                            configs.push( { priority: cfg.priority, html: _.template(RequiredConfigEntryHtml, {data:cfg}) } )
                            // only include items with labels
                        }
                        // (others might be included in future with an "expand" option, or priority option)
                    })
                }
                configs = configs.sort( function(a,b) { return b.priority - a.priority } )
                for (var c in configs) {
                    that.$('.config-table').append(configs[c].html)
                }
                // TODO add any manual config supplied by user (in previous turn visiting this tab)
            }
        },
        getConfigMap:function() {
            var map = {}
            $('.app-add-wizard-config-entry').each( function (index,elt) {
                map[$('#key',elt).val()] = 
                    $('#checkboxValue',elt).length ? $('#checkboxValue',elt).is(':checked') :
                    $('#value',elt).val()
            })
            return map;
        },
        selection:function (event) {
            var loc_id = $(event.currentTarget).val(),
                isNoneLocation = loc_id === NO_LOCATION_INDICATOR;
            var locationValid = isNoneLocation || this.locations.find(function (candidate) {
                return candidate.get("id")==loc_id;
            });
            if (!locationValid) {
                log("invalid location "+loc_id);
                this.showFailure("Invalid location "+loc_id);
                this.model.spec.set("locations",[]);
            } else {
                var index = $(event.currentTarget).parent().attr('rowId');
                this.model.spec.setLocationAtIndex(index, isNoneLocation ? undefined : loc_id);
            }
        },
        updateName:function () {
            var name = this.$('#application-name').val();
            if (name)
                this.model.spec.set("name", name);
            else
                this.model.spec.set("name", "");
        },
        validate:function () {
            this.model.spec.set("config", this.getConfigMap())
            if (this.model.spec.get("locations").length !== 0) {
                return true
            } else {
                this.showFailure("A location is required");
                return false;
            }
        },
        showFailure: function(text) {
            if (!text) text = "Failure performing the specified action";
            log("showing error: "+text);
            this.$('div.error-message .error-message-text').html(_.escape(text));
            // flash the error, but make sure it goes away (we do not currently have any other logic for hiding this error message)
            this.$('div.error-message').slideDown(250).delay(10000).slideUp(500);
        }
    })

    ModalWizard.StepPreview = Backbone.View.extend({
        className:'modal-body',
        initialize:function () {
            this.$el.html(_.template(PreviewHtml))
            this.model.spec.on("change", this.render, this)
        },
        beforeClose:function () {
            this.model.spec.off("change", this.render)
        },
        updateForState: function () {
            if (!this.model.spec.get("entities") || this.model.spec.get("entities").length==0) {
                delete this.model.spec.attributes["entities"]
            }
            if (!this.model.spec.get("name"))
                delete this.model.spec.attributes["name"]
            if (!this.model.spec.get("config") || _.keys(this.model.spec.get("config")).length==0) {
                delete this.model.spec.attributes["config"]
            }
            this.$('#app-summary').val(Util.toTextAreaString(specToCAMP(this.model.spec.toJSON())))
        },
        render:function () {
            this.delegateEvents()
            return this
        },
        validate:function () {
            // need locations, and type or entities
            if ((this.model.spec.get("locations").length > 0) && 
                (this.model.spec.get("type")!=null || 
                    this.model.spec.getEntities().length > 0)) {
                return true
            }
            
            if (this.model.spec.get("locations").length <= 0) {
                this.showFailure("A location is required");
                return false;
            }

            this.showFailure();
            return false
        },
        showFailure: function(text) {
            if (!text) text = "Failure performing the specified action";
            this.$('div.error-message .error-message-text').html(_.escape(text))
            this.$('div.error-message').slideDown(250).delay(10000).slideUp(500)
        }
    })

    return ModalWizard
})
