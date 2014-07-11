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
define([
    "underscore", "jquery", "backbone", "formatJson",
    "model/location", "model/entity",
    "view/catalog-add-location-modal",
    "text!tpl/catalog/page.html",
    "text!tpl/catalog/details-entity.html",
    "text!tpl/catalog/details-generic.html",
    "text!tpl/catalog/nav-entry.html",
    "text!tpl/catalog/details-location.html",

    "bootstrap", "jquery-form"
], function(_, $, Backbone, FormatJSON, Location, Entity, AddLocationModalView,
        CatalogPageHtml, DetailsEntityHtml, DetailsGenericHtml, EntryHtml, LocationDetailsHtml) {

    // Holds the currently active details type, e.g. applications, policies
    var activeDetails;

    // TODO: Loading item's details should perform page navigation
    var DetailsView = Backbone.View.extend({

        events: {
            "click .delete": "deleteItem"
        },

        initialize: function() {
            _.bindAll(this);
        },

        render: function(extraMessage) {
            this.$el.html("<div class='catalog-details'>" +
                "<h3>Select an entry on the left</h3>" +
                (extraMessage ? extraMessage : "") +
                "</div>");
        },

        show: function(model, template) {
            // Keep the previously open section open between items
            var open = this.$(".in").attr("id");
            var newHtml = $(template({model: model}));
            $(newHtml).find("#"+open).addClass("in");
            this.$el.html(newHtml);

            // rewire events. previous callbacks are removed automatically.
            this.delegateEvents()
        },

        showDetailsFor: function(model, template) {
            this.activeModel = model;
            var that = this;
            // Load the view with currently available data and refresh once the load is complete.
            // Only refreshes the view if the model changes and the user hasn't selected another
            // item while the load was executing.
            this.show(model, template);
            model.on("change", function() {
                if (that.activeModel.cid === model.cid) {
                    that.show(model, template);
                }
            });
            model.fetch()
                .fail(function(xhr, textStatus, errorThrown) {
                    console.log("error loading", model.id, ":", errorThrown);
                    if (that.activeModel.cid === model.cid) {
                        model.error = true;
                        that.show(model, template);
                    }
                })
                // Runs after the change event fires, or after the xhr completes
                .always(function () {
                    model.off("change");
                });
        },

        deleteItem: function(event) {
            // Could use wait flag to block removal of model from collection
            // until server confirms deletion and success handler to perform
            // removal. Useful if delete fails for e.g. lack of entitlement.
            this.activeModel.destroy();
            var displayName = $(event.currentTarget).data("name");
            this.render(displayName ? "Deleted " + displayName : "");
        }
    });

    var Catalog = Backbone.Collection.extend({
        initialize: function(models, options) {
            this.name = options["name"];
            if (!this.name) {
                throw new Error("Catalog collection must know its name");
            }
            _.bindAll(this);
        },
        url: function() {
            return "/v1/catalog/" + this.name;
        }
    });

    var accordionBodyTemplate = _.template(
        "<div class='accordion-head capitalized'><%= name %></div>" +
        "<div class='accordion-body' style='display: <%= display %>'></div>");

    /** Use to fill single accordion view list. */
    var AccordionItemView = Backbone.View.extend({
        tag: "div",
        className: "accordion-item",
        events: {
            'click .accordion-head': 'toggle',
            'click .accordion-nav-row': 'showDetails'
        },

        initialize: function() {
            _.bindAll(this);
            this.name = this.options.name;
            if (!this.name) {
                throw new Error("Name should have been given for accordion entry");
            }

            // Generic templates
            this.template = _.template(this.options.template || EntryHtml);
            this.detailsTemplate = _.template(this.options.detailsTemplate || DetailsGenericHtml);

            // Returns template applied to function arguments. Alter if collection altered.
            // Will be run in the context of the AccordionItemView.
            this.templateArgs = this.options.templateArgs || function(model, index) {
                return {type: model.get("type"), id: model.get("id")};
            };

            // undefined argument is used for existing model items
            var collectionModel = this.options.model || Backbone.Model;
            this.collection = this.options.collection || new Catalog(undefined, {
                name: this.name,
                model: collectionModel
            });
            // Refreshes entries list when the collection is synced with the server or
            // any of its members are destroyed.
            this.collection
                .on("sync", this.renderEntries)
                .on("destroy", this.renderEntries);
            this.refresh();
        },

        beforeClose: function() {
            this.collection.off();
        },

        render: function() {
            this.$el.html(accordionBodyTemplate({
                name: this.name,
                display: this.options.autoOpen ? "block" : "none"
            }));
            this.renderEntries();
            return this;
        },

        renderEntries: function() {
            var templater = function(model, index) {
                var args = _.extend({cid: model.cid}, this.templateArgs(model));
                return this.template(args);
            };
            var elements = this.collection.map(templater, this);
            this.$(".accordion-body")
                .empty()
                .append(elements.join(''));
            // Rehighlight active model
            if (this.activeCid && activeDetails === this.name) {
                $(".accordion-nav-row").removeClass("active");
                this.setActiveItem(this.$("[data-cid='"+this.activeCid+"'"));
            }
        },

        refresh: function() {
            this.collection.fetch();
        },

        setActiveItem: function($element) {
            $(".accordion-nav-row").removeClass("active");
            $element.addClass("active");
            activeDetails = this.name;
        },

        showDetails: function(event) {
            var $event = $(event.currentTarget);
            if (!$event.hasClass("active")) {
                this.setActiveItem($event);
                var cid = this.activeCid = $(event.currentTarget).data("cid");
                var model = this.collection.get(cid);
                this.options.details.showDetailsFor(model, this.detailsTemplate);
            }
        },

        toggle: function() {
            var body = this.$(".accordion-body");
            var hidden = this.hidden = body.css("display") == "none";
            if (hidden) {
                this.$el.addClass('active');
                body.removeClass("hide").slideDown('fast');
            } else {
                this.$el.removeClass('active');
                body.slideUp('fast')
            }
        }
    });

    var CatalogResourceView = Backbone.View.extend({
        tagName:"div",
        className:"container container-fluid",
        entryTemplate:_.template(EntryHtml),

        events: {
            'click .refresh':'refresh',
            'click #add-new-thing':'createNewThing',
            'click #add-new-entity':'addNewCatalogResource',
            'click #new-entity-submit':'newEntitySubmit',
            'click #add-new-location':'addLocation',
            'click .delete-location':'deleteLocation'
        },

        initialize: function() {
            $(".nav1").removeClass("active");
            $(".nav1_catalog").addClass("active");
            this.detailsView = new DetailsView();
            this.accordion = this.options.accordion || [
                new AccordionItemView({
                    name: "applications",
                    details: this.detailsView,
                    detailsTemplate: DetailsEntityHtml,
                    model: Entity.Model,
                    autoOpen: true
                }),
                new AccordionItemView({
                    name: "entities",
                    details: this.detailsView,
                    detailsTemplate: DetailsEntityHtml,
                    model: Entity.Model
                }),
                new AccordionItemView({
                    name: "policies",
                    detailsTemplate: DetailsGenericHtml,
                    details: this.detailsView
                }),
                new AccordionItemView({
                    name: "locations",
                    details: this.detailsView,
                    detailsTemplate: LocationDetailsHtml,
                    collection: this.options.locations,
                    templateArgs: function(location, index) {
                        return {
                            type: location.getPrettyName(),
                            id: location.getLinkByName("self")
                        };
                    }
                })
            ];
            _.bindAll(this);
        },

        beforeClose: function() {
            _.invoke(this.accordion, 'close');
        },

        render: function(eventName) {
            this.$el.html(_.template(CatalogPageHtml, {}));
            this.detailsView.$el = this.$("#details");
            this.detailsView.render();
            var parent = this.$(".catalog-accordion-parent");
            _.each(this.accordion, function(child) {
                parent.append(child.render().$el);
            });
            return this
        },

        /** Refreshes the contents of each accordion pane */
        refresh: function() {
            _.invoke(this.accordion, 'refresh');
        },

        createNewThing: function(event) {
            var that = this;
            if (_.contains(that.genericTabs, that.activeAccordion)) {
                that.addNewCatalogResource(event)
            } else if (that.activeAccordion=='locations') {
                that.addLocation(event)
            } else {
                that.$('#accordion-empty-to-create-info-message').slideDown('slow').delay(2000).slideUp('slow')
            }
        },

        addNewCatalogResource: function(event) {
            $('#new-entity-modal').modal('show')
        },

        newEntitySubmit: function(event) {
            var $entityForm = $('#new-entity-form'),
                $entityModal = $('#new-entity-modal'),
                self = this;
            $entityModal.fadeTo(500,0.5);
            var options = {
                url:'/v1/catalog/',
                type:'post',
                success: function(data) {
                    $entityModal.modal('hide');
                    $entityModal.fadeTo(500,1);
                    self.refresh()
                },
                error: function(data) {
                    $entityModal.fadeTo(100,1).delay(200).fadeTo(200,0.2).delay(200).fadeTo(200,1);
                    // TODO render the error (want better feedback than this poor-man's flashing)
                }
            };
            $entityForm.ajaxSubmit(options);
            return false
        },
        
        addLocation: function(event) {
            var locationModalView = new AddLocationModalView({
                model:new Location.Model(),
                appRouter:this.options.appRouter
            });
            this.$('#new-location-modal').replaceWith(locationModalView.render().$el);
            this.$('#new-location-modal').modal('show');
        },

        deleteLocation: function(event) {
            this.model.get(event.currentTarget['id']).destroy();
        }
        
    });
    
    return CatalogResourceView
});
