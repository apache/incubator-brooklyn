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
    "underscore", "jquery", "backbone", "brooklyn",
    "model/location", "model/entity",
    "text!tpl/catalog/page.html",
    "text!tpl/catalog/details-entity.html",
    "text!tpl/catalog/details-generic.html",
    "text!tpl/catalog/details-location.html",
    "text!tpl/catalog/add-catalog-entry.html",
    "text!tpl/catalog/add-yaml.html",
    "text!tpl/catalog/add-location.html",
    "text!tpl/catalog/nav-entry.html",

    "bootstrap", "jquery-form"
], function(_, $, Backbone, Brooklyn,
        Location, Entity,
        CatalogPageHtml, DetailsEntityHtml, DetailsGenericHtml, LocationDetailsHtml,
        AddCatalogEntryHtml, AddYamlHtml, AddLocationHtml, EntryHtml) {

    // Holds the currently active details type, e.g. applications, policies. Bit of a workaround
    // to share the active view with all instances of AccordionItemView, so clicking the 'reload
    // catalog' button (handled by the parent of the AIVs) does not apply the 'active' class to
    // more than one element.
    var activeDetailsView;

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
                var newHtml = $(template({model: model, viewName: that.options.name}));
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
            var that = this;
            var displayName = $(event.currentTarget).data("name") || "item";
            this.activeModel.destroy({
                success: function() {
                    that.renderEmpty("Deleted " + displayName);
                },
                error: function(info) {
                    that.renderEmpty("Unable to permanently delete " + displayName+". Deletion is temporary, client-side only.");
                }
            });
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
                if (initialView == "entity") initialView = "yaml";
                
                this.$("[data-context='"+initialView+"']").addClass("active");
                this.showFormForType(initialView)
            }
            return this;
        },
        clearWithHtml: function(template) {
            if (this.contextView) this.contextView.close();
            this.context = undefined;
            this.$(".btn").removeClass("active");
            this.$("#catalog-add-form").html(template);
        },
        beforeClose: function () {
            if (this.contextView) this.contextView.close();
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
            if (type == "yaml" || type == "entity") {
                this.contextView = newYamlForm(this, this.options.parent);
            } else if (type == "location") {
                this.contextView = newLocationForm(this, this.options.parent);
            } else if (type !== undefined) {
                console.log("unknown catalog type " + type);
                this.showFormForType("yaml");
                return;
            }
            Backbone.history.navigate("/v1/catalog/new/" + type);
            this.$("#catalog-add-form").html(this.contextView.$el);
        }
    });

    function newYamlForm(addView, addViewParent) {
        return new Brooklyn.view.Form({
            template: _.template(AddYamlHtml),
            onSubmit: function (model) {
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
                        if (_.size(data)==0) {
                          addView.clearWithHtml( "No items supplied." );
                        } else {
                          addView.clearWithHtml( "Added: "+_.escape(_.keys(data).join(", ")) 
                            + (_.size(data)==1 ? ". Loading..." : "") );
                          addViewParent.loadAnyAccordionItem(_.size(data)==1 ? _.keys(data)[0] : undefined);
                        }
                    })
                    .fail(function (xhr, status, error) {
                        submitButton.button("reset");
                        self.$(".catalog-save-error")
                            .removeClass("hide")
                            .find(".catalog-error-message")
                            .html(_.escape(Brooklyn.util.extractError(xhr, "Could not add catalog item:\n'n" + error)));
                    });
            }
        });
    }

    // Could adapt to edit existing locations too.
    function newLocationForm(addView, addViewParent) {
        // Renders with config key list
        var body = new (Backbone.View.extend({
            beforeClose: function() {
                if (this.configKeyList) {
                    this.configKeyList.close();
                }
            },
            render: function() {
                this.configKeyList = new Brooklyn.view.ConfigKeyInputPairList().render();
                var template = _.template(AddLocationHtml);
                this.$el.html(template);
                this.$("#new-location-config").html(this.configKeyList.$el);
            },
            showError: function (message) {
                self.$(".catalog-save-error")
                    .removeClass("hide")
                    .find(".catalog-error-message")
                    .html(message);
            }
        }));
        var form = new Brooklyn.view.Form({
            body: body,
            model: Location.Model,
            onSubmit: function (location) {
                var configKeys = body.configKeyList.getConfigKeys();
                if (!configKeys.displayName) {
                    configKeys.displayName = location.get("name");
                }
                var submitButton = this.$(".catalog-submit-button");
                // "loading" is an indicator to Bootstrap, not a string to display
                submitButton.button("loading");
                location.set("config", configKeys);
                location.save()
                    .done(function (data) {
                        addView.clearWithHtml( "Added: "+data.id+". Loading..." ); 
                        addViewParent.loadAccordionItem("locations", data.id);
                    })
                    .fail(function (response) {
                        submitButton.button("reset");
                        body.showError(Brooklyn.util.extractError(response));
                    });
            }
        });

        return form;
    }

    var Catalog = Backbone.Collection.extend({
        modelX: Backbone.Model.extend({
          url: function() {
            return "/v1/catalog/" + this.name + "/" + this.id + "?allVersions=true";
          }
        }),
        initialize: function(models, options) {
            this.name = options["name"];
            if (!this.name) {
                throw new Error("Catalog collection must know its name");
            }
            //this.model is a constructor so it shouldn't be _.bind'ed to this
            //It actually works when a browser provided .bind is used, but the
            //fallback implementation doesn't support it.
            var that = this; 
            var model = this.model.extend({
              url: function() {
                return "/v1/catalog/" + that.name + "/" + this.id.split(":").join("/");
              }
            });
            _.bindAll(this);
            this.model = model;
        },
        url: function() {
            return "/v1/catalog/" + this.name+"?allVersions=true";
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
                return {type: model.getVersionedAttr("type"), id: model.get("id")};
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

        singleItemTemplater: function(isChild, model, index) {
            var args = _.extend({
                    cid: model.cid,
                    isChild: isChild,
                    extraClasses: (activeDetailsView == this.name && model.cid == this.activeCid) ? "active" : ""
                }, this.entryTemplateArgs(model));
            return this.template(args);
        },

        renderEntries: function() {
            var elements = this.collection.map(_.partial(this.singleItemTemplater, false), this);
            this.updateContent(elements.join(''));
        },

        updateContent: function(markup) {
            this.$(".accordion-body")
                .empty()
                .append(markup);
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
                this.options.onItemSelected(activeDetailsView, model, $event);
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
    
    var AccordionEntityView = AccordionItemView.extend({
        renderEntries: function() {
            var symbolicNameFn = function(model) {return model.get("symbolicName")};
            var groups = this.collection.groupBy(symbolicNameFn);
            var orderedIds = _.uniq(this.collection.map(symbolicNameFn));

            function getLatestStableVersion(items) {
                //the server sorts items by descending version, snapshots at the back
                return items[0];
            }

            var catalogTree = _.map(orderedIds, function(symbolicName) {
                var group = groups[symbolicName];
                var root = getLatestStableVersion(group);
                var children = _.reject(group, function(model) {return root.id == model.id;});
                return {root: root, children: children};
            });

            var templater = function(memo, item, index) {
                memo.push(this.singleItemTemplater(false, item.root));
                return memo.concat(_.map(item.children, _.partial(this.singleItemTemplater, true), this));
            };

            var elements = _.reduce(catalogTree, templater, [], this);
            this.updateContent(elements.join(''));
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
                "applications": new AccordionEntityView({
                    name: "applications",
                    singular: "application",
                    onItemSelected: _.partial(this.showCatalogItem, DetailsEntityHtml),
                    model: Entity.Model,
                    autoOpen: !this.options.kind || this.options.kind == "applications"
                }),
                "entities": new AccordionEntityView({
                    name: "entities",
                    singular: "entity",
                    onItemSelected: _.partial(this.showCatalogItem, DetailsEntityHtml),
                    model: Entity.Model,
                    autoOpen: this.options.kind == "entities"
                }),
                "policies": new AccordionEntityView({
                    // TODO needs parsing, and probably its own model
                    // but cribbing "entity" works for now 
                    // (and not setting a model can cause errors intermittently)
                    onItemSelected: _.partial(this.showCatalogItem, DetailsEntityHtml),
                    name: "policies",
                    singular: "policy",
                    model: Entity.Model,
                    autoOpen: this.options.kind == "policies"
                }),
                "locations": new AccordionItemView({
                    name: "locations",
                    singular: "location",
                    onItemSelected: _.partial(this.showCatalogItem, LocationDetailsHtml),
                    collection: this.options.locations,
                    autoOpen: this.options.kind == "locations",
                    entryTemplateArgs: function (location, index) {
                        return {
                            type: location.getIdentifierName(),
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

        loadAnyAccordionItem: function (id) {
            this.loadAccordionItem("entities", id);
            this.loadAccordionItem("applications", id);
            this.loadAccordionItem("policies", id);
            this.loadAccordionItem("locations", id);
        },

        loadAccordionItem: function (kind, id) {
            if (!this.accordion[kind]) {
                console.error("No accordion for: " + kind);
            } else {
                var accordion = this.accordion[kind];
                var self = this;
                // reset is needed because we rely on server's ordering;
                // without it, server additions are placed at end of list
                accordion.collection.fetch({reset: true})
                    .then(function() {
                        var model = accordion.collection.get(id);
                        if (!model) {
                            // if a version is supplied, try it without a version - needed for locations, navigating after deletion
                            if (id && id.split(":").length>1) {
                                model = accordion.collection.get( id.split(":")[0] );
                            }
                        }
                        if (!model) {
                            // if an ID is supplied without a version, look for first matching version (should be newest)
                            if (id && id.split(":").length==1 && accordion.collection.models) {
                                model = _.find(accordion.collection.models, function(m) { 
                                    return m && m.id && m.id.startsWith(id+":");
                                });
                            }
                        }
                        // TODO could look in collection for any starting with ID
                        if (model) {
                            Backbone.history.navigate("/v1/catalog/"+kind+"/"+id);
                            activeDetailsView = kind;
                            accordion.activeCid = model.cid;
                            accordion.options.onItemSelected(kind, model);
                            accordion.show();
                        } else {
                            // catalog item not found, or not found yet (it might be reloaded and another callback will try again)
                        }
                    });
            }
        },

        showCatalogItem: function(template, viewName, model, $target) {
            this.$(".accordion-nav-row").removeClass("active");
            if ($target) {
                $target.addClass("active");
            } else {
                this.$("[data-cid=" + model.cid + "]").addClass("active");
            }
            var newView = new CatalogItemDetailsView({
                model: model,
                template: template,
                name: viewName
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
