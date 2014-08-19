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
    "underscore", "jquery", "backbone", "formatJson", "brooklyn",
    "model/location", "model/entity",
    "text!tpl/catalog/page.html",
    "text!tpl/catalog/details-entity.html",
    "text!tpl/catalog/details-generic.html",
    "text!tpl/catalog/details-location.html",
    "text!tpl/catalog/add-catalog-entry.html",
    "text!tpl/catalog/add-entity.html",
    "text!tpl/catalog/nav-entry.html",

    "bootstrap", "jquery-form"
], function(_, $, Backbone, FormatJSON, Brooklyn,
        Location, Entity,
        CatalogPageHtml, DetailsEntityHtml, DetailsGenericHtml, LocationDetailsHtml,
        AddCatalogEntryHtml, AddEntityHtml, EntryHtml) {

    // Holds the currently active details type, e.g. applications, policies. Bit of a workaround
    // to share the active view with all instances of AccordionItemView, so clicking the 'reload
    // catalog' button (handled by the parent of the AIVs) does not apply the 'active' class to
    // more than one element.
    var activeDetailsView;

    // TODO: Loading item's details should perform page navigation
    var CatalogItemDetailsView = Backbone.View.extend({

        events: {
            "click .delete": "deleteItem"
        },

        initialize: function() {
            _.bindAll(this);
            this.options.template = _.template(this.options.template || DetailsGenericHtml);
        },

        render: function() {
            if (!this.options.model) {
                return this.renderEmpty();
            } else {
                return this.renderDetails();
            }
        },

        renderEmpty: function(extraMessage) {
            this.$el.html("<div class='catalog-details'>" +
                "<h3>Select an entry on the left</h3>" +
                (extraMessage ? extraMessage : "") +
                "</div>");
            return this;
        },

        renderDetails: function() {
            var that = this,
                model = this.options.model,
                template = this.options.template;
            var show = function() {
                // Keep the previously open section open between items. Duplication between
                // here and setDetailsView, below. This case handles view refreshes from this
                // view directly (e.g. when indicating an error), below handles keeping the
                // right thing open when navigating from view to view.
                var open = this.$(".in").attr("id");
                var newHtml = $(template({model: model}));
                $(newHtml).find("#"+open).addClass("in");
                that.$el.html(newHtml);
                // rewire events. previous callbacks are removed automatically.
                that.delegateEvents()
            };

            this.activeModel = model;
            // Load the view with currently available data and refresh once the load is complete.
            // Only refreshes the view if the model changes and the user hasn't selected another
            // item while the load was executing.
            show();
            model.on("change", function() {
                if (that.activeModel.cid === model.cid) {
                    show();
                }
            });
            model.fetch()
                .fail(function(xhr, textStatus, errorThrown) {
                    console.log("error loading", model.id, ":", errorThrown);
                    if (that.activeModel.cid === model.cid) {
                        model.error = true;
                        show();
                    }
                })
                // Runs after the change event fires, or after the xhr completes
                .always(function () {
                    model.off("change");
                });
            return this;
        },

        deleteItem: function(event) {
            // Could use wait flag to block removal of model from collection
            // until server confirms deletion and success handler to perform
            // removal. Useful if delete fails for e.g. lack of entitlement.
            this.activeModel.destroy();
            var displayName = $(event.currentTarget).data("name");
            this.renderEmpty(displayName ? "Deleted " + displayName : "");
        }
    });

    var AddCatalogEntryView = Backbone.View.extend({
        template: _.template(AddCatalogEntryHtml),
        events: {
            "click .show-context": "showContext"
        },
        initialize: function() {
            _.bindAll(this);
        },
        render: function (initialView) {
            this.$el.html(this.template());
            if (initialView) {
                this.$("[data-context='"+initialView+"']").addClass("active");
                this.showFormForType(initialView)
            }
            return this;
        },
        beforeClose: function () {
            if (this.contextView) {
                this.contextView.close();
            }
        },
        showContext: function(event) {
            var $event = $(event.currentTarget);
            var context = $event.data("context");
            if (this.context !== context) {
                if (this.contextView) {
                    this.contextView.close();
                }
                this.showFormForType(context)
            }
        },
        showFormForType: function (type) {
            this.context = type;
            if (type == "entity") {
                this.contextView = newEntityForm(this.options.parent);
            } else if (type !== undefined) {
                console.log("unknown catalog type " + type);
                this.showFormForType("entity");
                return;
            }
            Backbone.history.navigate("/v1/catalog/new/" + type);
            this.$("#catalog-add-form").html(this.contextView.$el);
        }
    });

    function newEntityForm(parent) {
        return new Brooklyn.view.Form({
            template: AddEntityHtml,
            onSubmit: function (model) {
                console.log("Submit entity", model.get("yaml"));
                var submitButton = this.$(".catalog-submit-button");
                // "loading" is an indicator to Bootstrap, not a string to display
                submitButton.button("loading");
                var self = this;
                var options = {
                    url: "/v1/catalog/",
                    data: model.get("yaml"),
                    processData: false,
                    type: "post"
                };
                $.ajax(options)
                    .done(function (data, status, xhr) {
                        // Can extract location of new item with:
                        //model.url = Brooklyn.util.pathOf(xhr.getResponseHeader("Location"));
                        parent.loadAccordionItem("entities", data.id);
                    })
                    .fail(function (xhr, status, error) {
                        var message;
                        try {
                            message = JSON.parse(xhr.responseText).message;
                        } catch (e) {
                            message = "Error adding catalog item: " + error;
                        }
                        submitButton.button("reset");
                        self.$(".catalog-save-error")
                            .removeClass("hide")
                            .find(".catalog-error-message")
                            .html(message);
                    });
            }
        });
    }

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

    /** Use to fill single accordion view list. */
    var AccordionItemView = Backbone.View.extend({
        tag: "div",
        className: "accordion-item",
        events: {
            'click .accordion-head': 'toggle',
            'click .accordion-nav-row': 'showDetails'
        },
        bodyTemplate: _.template(
            "<div class='accordion-head capitalized'><%= name %></div>" +
            "<div class='accordion-body' style='display: <%= display %>'></div>"),

        initialize: function() {
            _.bindAll(this);
            this.name = this.options.name;
            if (!this.name) {
                throw new Error("Name should have been given for accordion entry");
            } else if (!this.options.onItemSelected) {
                throw new Error("onItemSelected(model, element) callback should have been given for accordion entry");
            }

            // Generic templates
            this.template = _.template(this.options.template || EntryHtml);

            // Returns template applied to function arguments. Alter if collection altered.
            // Will be run in the context of the AccordionItemView.
            this.entryTemplateArgs = this.options.entryTemplateArgs || function(model, index) {
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
            this.$el.html(this.bodyTemplate({
                name: this.name,
                display: this.options.autoOpen ? "block" : "none"
            }));
            this.renderEntries();
            return this;
        },

        renderEntries: function() {
            var name = this.name, active = this.activeCid;
            var templater = function(model, index) {
                var args = _.extend({
                        cid: model.cid,
                        extraClasses: (activeDetailsView == name && model.cid == active) ? "active" : ""
                    }, this.entryTemplateArgs(model));
                return this.template(args);
            };
            var elements = this.collection.map(templater, this);
            this.$(".accordion-body")
                .empty()
                .append(elements.join(''));
        },

        refresh: function() {
            this.collection.fetch();
        },

        showDetails: function(event) {
            var $event = $(event.currentTarget);
            var cid = $event.data("cid");
            if (activeDetailsView !== this.name || this.activeCid !== cid) {
                activeDetailsView = this.name;
                this.activeCid = cid;
                var model = this.collection.get(cid);
                Backbone.history.navigate("v1/catalog/" + this.name + "/" + model.id);
                this.options.onItemSelected(model, $event);
            }
        },

        toggle: function() {
            var body = this.$(".accordion-body");
            var hidden = this.hidden = body.css("display") == "none";
            if (hidden) {
                body.removeClass("hide").slideDown('fast');
            } else {
                body.slideUp('fast')
            }
        },

        show: function() {
            var body = this.$(".accordion-body");
            var hidden = this.hidden = body.css("display") == "none";
            if (hidden) {
                body.removeClass("hide").slideDown('fast');
            }
        }
    });

    // Controls whole page. Parent of accordion items and details view.
    var CatalogResourceView = Backbone.View.extend({
        tagName:"div",
        className:"container container-fluid",
        entryTemplate:_.template(EntryHtml),

        events: {
            'click .refresh':'refresh',
            'click #add-new-thing': 'createNewThing'
        },

        initialize: function() {
            $(".nav1").removeClass("active");
            $(".nav1_catalog").addClass("active");
            // Important that bind happens before accordion object is created. If it happens after
            // `this' will not be set correctly for the onItemSelected callbacks.
            _.bindAll(this);
            this.accordion = this.options.accordion || {
                "applications": new AccordionItemView({
                    name: "applications",
                    onItemSelected: _.partial(this.showCatalogItem, DetailsEntityHtml),
                    model: Entity.Model,
                    autoOpen: !this.options.kind || this.options.kind == "applications"
                }),
                "entities": new AccordionItemView({
                    name: "entities",
                    onItemSelected: _.partial(this.showCatalogItem, DetailsEntityHtml),
                    model: Entity.Model,
                    autoOpen: this.options.kind == "entities"
                }),
                "policies": new AccordionItemView({
                    onItemSelected: _.partial(this.showCatalogItem, DetailsGenericHtml),
                    name: "policies",
                    autoOpen: this.options.kind == "policies"
                }),
                "locations": new AccordionItemView({
                    name: "locations",
                    onItemSelected: _.partial(this.showCatalogItem, LocationDetailsHtml),
                    collection: this.options.locations,
                    autoOpen: this.options.kind == "locations",
                    entryTemplateArgs: function (location, index) {
                        return {
                            type: location.getPrettyName(),
                            id: location.getLinkByName("self")
                        };
                    }
                })
            };
        },

        beforeClose: function() {
            _.invoke(this.accordion, 'close');
        },

        render: function() {
            this.$el.html(_.template(CatalogPageHtml, {}));
            var parent = this.$(".catalog-accordion-parent");
            _.each(this.accordion, function(child) {
                parent.append(child.render().$el);
            });
            if (this.options.kind === "new") {
                this.createNewThing(this.options.id);
            } else if (this.options.kind && this.options.id) {
                this.loadAccordionItem(this.options.kind, this.options.id)
            } else {
                // Show empty details view to start
                this.setDetailsView(new CatalogItemDetailsView().render());
            }
            return this
        },

        /** Refreshes the contents of each accordion pane */
        refresh: function() {
            _.invoke(this.accordion, 'refresh');
        },

        createNewThing: function (type) {
            // Discard if it's the jquery event object.
            if (!_.isString(type)) {
                type = undefined;
            }
            var viewName = "createNewThing";
            if (!type) {
                Backbone.history.navigate("/v1/catalog/new");
            }
            activeDetailsView = viewName;
            this.$(".accordion-nav-row").removeClass("active");
            var newView = new AddCatalogEntryView({
                parent: this
            }).render(type);
            this.setDetailsView(newView);
        },

        loadAccordionItem: function (kind, id) {
            if (!this.accordion[kind]) {
                console.error("No accordion for: " + kind);
            } else {
                var accordion = this.accordion[kind];
                accordion.collection.fetch()
                    .then(function() {
                        var model = accordion.collection.get(id);
                        if (!model) {
                            console.log("Accordion for " + kind + " has no element with id " + id);
                        } else {
                            activeDetailsView = kind;
                            accordion.activeCid = model.cid;
                            accordion.options.onItemSelected(model);
                            accordion.show();
                        }
                    });
            }
        },

        showCatalogItem: function(template, model, $target) {
            this.$(".accordion-nav-row").removeClass("active");
            if ($target) {
                $target.addClass("active");
            } else {
                this.$("[data-cid=" + model.cid + "]").addClass("active");
            }
            var newView = new CatalogItemDetailsView({
                model: model,
                template: template
            }).render();
            this.setDetailsView(newView)
        },

        setDetailsView: function(view) {
            this.$("#details").html(view.el);
            if (this.detailsView) {
                // Try to re-open sections that were previously visible.
                var openedItem = this.detailsView.$(".in").attr("id");
                if (openedItem) {
                    view.$("#" + openedItem).addClass("in");
                }
                this.detailsView.close();
            }
            this.detailsView = view;
        }
    });
    
    return CatalogResourceView
});
