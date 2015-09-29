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
 * Sub-View to render the Application tree.
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "view/viewutils",
    "model/app-tree", "./entity-details", "model/entity-summary", "model/application",
    "text!tpl/apps/tree-item.html", "text!tpl/apps/tree-empty.html", "text!tpl/apps/details.html", "text!tpl/apps/entity-not-found.html"
], function (_, $, Backbone, ViewUtils,
             AppTree, EntityDetailsView, EntitySummary, Application,
             TreeItemHtml, TreeEmptyHtml, EntityDetailsEmptyHtml, EntityNotFoundHtml) {

    var treeViewTemplate = _.template(TreeItemHtml);
    var notFoundTemplate = _.template(EntityNotFoundHtml);


    var ApplicationTreeView = Backbone.View.extend({
        template: treeViewTemplate,
        hoverTimer: null,

        events: {
            'click span.entity_tree_node .tree-change':'treeChange',
            'click span.entity_tree_node':'displayEntity'
        },
            this.collection.on('all', this.modelEvent, this)
            this.collection.on('change', this.modelChange, this)
            this.collection.on('remove', this.modelRemove, this)
            this.collection.on('add', this.modelAdd, this)
            this.collection.on('reset', this.renderFull, this)

        initialize: function() {
            _.bindAll(this);
        },

        beforeClose: function() {
            this.collection.off("reset", this.renderFull);
            if (this.detailsView) this.detailsView.close();
        },
        
        modelChange: function (child) {
            this.updateNode(child.id)
        },
        modelAdd: function (child) {
            this.updateNode(child.id)
        },
        modelRemove: function (child) {
            this.removeNode(child.id)
        },

        modelEvent: function(eventName, event, x) {
            if (/^change/i.test(eventName) || eventName == "remove" || eventName == "add" ||
                    eventName == "reset" ||
                    // above are handled; below is no-op
                    eventName == "sync" || eventName == "request")
                return;

            if (eventName == "error") {
                log("model error in application-tree - has the internet vanished?")
                // ignore; app-explorer should clear the view
                return;
            }

            // don't think we get other events, but just in case:
            log("unhandled model event");
            log(eventName);
            log(event);
            log(x);
        },

        removeNode: function(id) {
            $('#'+id, this.$el).parent().remove()
            // collection seems sometimes to have children nodes;
            // not sure why, but that's okay for now
            if (this.collection.getApplications().length==0)
                this.renderFull();
        },
        
        updateNode: function(id, parentId, isApp) {
            var that = this;
            var nModel = that.collection.get(id);
            var node = $('#'+id, that.$el)
            
            if (!isApp) {
                // autodiscover whether this is an app, looking at the model and the tree
                // (at least one should be available -- probably always the former, but...)
                if (nModel) { isApp = (id == nModel.get('applicationId')); }
                else if (!isApp && node && node.parent().data('depth')==0) isApp = true;
            }

            if (!isApp && !parentId && nModel)
                parentId = nModel.get('parentId');
            if (!isApp && !parentId && node)
                parentId = node.closest("entity_tree_node_wrapper").data('parentId');
            if (!isApp && !parentId) {
                log("no parentId yet available for "+id+"; skipping;")
                return false;
            }
            
            var statusIconUrl = nModel
                ? ViewUtils.computeStatusIconInfo(nModel.get("serviceUp"),nModel.get("serviceState")).url
                : null;

            var newNode = this.template({
                id:id,
                parentId:parentId,
                model:nModel,
                statusIconUrl:statusIconUrl
            })

            if (!node.length) {
                // node does not exist, so add it
                var parentsChildren, depth;
                
                if (isApp) {
                    parentsChildren = $('.lozenge-app-tree-wrapper', that.$el);
                    if (!parentsChildren.length) {
                        // entire view must be created
                        that.$el.html(
                                '<div class="navbar_main_wrapper treeloz">'+
                                '<div id="tree-list" class="navbar_main treeloz">'+
                                '<div class="lozenge-app-tree-wrapper">'+
                                '</div></div></div>');
                        parentsChildren = $('.lozenge-app-tree-wrapper', that.$el);
                    }
                    depth = 0;
                } else {
                    var parent = $('#'+parentId, that.$el)
                    if (!parent.length) {
                        // see if we can load the parent
                        if (this.updateNode(parentId)) {
                            parent = $('#'+parentId, that.$el);
                            if (!parent.length) {
                                log("no parent element yet available for "+id+" ("+parentId+") after parent load; skipping")
                                return false;                                
                            }
                        } else {
                            log("no parent element yet available for "+id+" ("+parentId+"); skipping")
                            return false;
                        }
                    }
                    parentsChildren = $(parent.parent().children('.node-children'));
                    depth = parent.parent().data("depth")+1
                }

                // add it, with surrounding html, in parent's node-children child
                // tildes in sortKey force entities with no name to bottom of list (z < ~).
                var entityName = nModel && nModel.get("name")
                        ? nModel.get("name")
                        : this.collection.getEntityNameFromId(id);
                var sortKey = (entityName ? entityName.toLowerCase() : "~~~") + "     " + id.toLowerCase();
                var newNodeWrapper = $(
                        '<div data-sort-key="'+sortKey+'" class="toggler-group tree-box '+
                            (depth==0 ? "outer" : "inner "+(depth%2==1 ? "depth-odd" : "depth-even")+
                                (depth==1 ? " depth-first" : "")) + '" data-depth="'+depth+'">'+
                        '<div id="'+id+'" class="entity_tree_node_wrapper"></div>'+
                        '<div class="toggler-target hide node-children"></div>'+
                        '</div>')
                $('#'+id, newNodeWrapper).html(newNode);

                // Maintain entities sorted by name, then id.
                var placed = false;
                var contender = $(".toggler-group", parentsChildren).first();
                while (contender.length && !placed) {
                    var contenderKey = contender.data("sort-key");
                    if (sortKey < contenderKey) {
                        contender.before(newNodeWrapper);
                        placed = true;
                    } else {
                        contender = contender.next(".toggler-group", parentsChildren);
                    }
                }
                if (!placed) {
                    parentsChildren.append(newNodeWrapper);
                }
                this.addEventsToNode(parentsChildren)
            } else {
                // updating
                var $node = $(node),
                    $newNode = $(newNode);
                
                // preserve old display status (just chevron direction at present)
                if ($node.find('.tree-node-state').hasClass('icon-chevron-down')) {
                    $newNode.find('.tree-node-state').removeClass('icon-chevron-right').addClass('icon-chevron-down')
                    // and if visible, see if any children have been added
                    var children = nModel.get("children");
                    var newChildren = []
                    _.each(children, function(child) {
                        var childId = child.id;
                        if (!that.collection.get(childId)) {
                            newChildren.push(childId);
                        }
                    })
                    if (newChildren.length) {
                        // reload if new child ID we don't recognise
                        this.collection.includeEntities(newChildren);
                        this.collection.fetch()
                    }
                }

                $(node).html($newNode)
                this.addEventsToNode($(node))
            }
            return true;
        },

        renderFull: function() {
            var that = this;
            this.$el.empty();

            // Display tree and highlight the selected entity.
            if (this.collection.getApplications().length == 0) {
                that.$el.append(_.template(TreeEmptyHtml));
            } else {
                _.each(this.collection.getApplications(), function(appId) {
                    that.updateNode(appId, null, true);
                });
                
                _.each(this.collection.getNonApplications(), function(id) {
                    that.updateNode(id);
                });
            }

            this.highlightEntity();

            // Render the details for the selected entity.
            if (this.detailsView) {
                this.detailsView.render();
            } else {
                // if nothing selected, select the first application
                if (!this.collection.isEmpty()) {
                    var app0 = this.collection.first().id;
                    _.defer(function () {
                        if (!that.selectedEntityId)
                            that.displayEntityId(app0, app0);
                    });
                } else {
                    _.defer(function() {
                        $("div#details").html(_.template(EntityDetailsEmptyHtml));
                        $("div#details").find("a[href='#summary']").tab('show');
                    });
                }
            }
            return this;
        },

        addEventsToNode: function($node) {
            var that = this;

            // show the "light-popup" (expand / expand all / etc) menu
            // if user hovers for 500ms. surprising there is no option for this (hover delay).
            // also, annoyingly, clicks around the time the animation starts don't seem to get handled
            // if the click is in an overlapping reason; this is why we position relative top: 12px in css
            $('.light-popup', $node).parent().parent().hover(
                    function(parent) {
                        that.cancelHoverTimer();
                        that.hoverTimer = setTimeout(function() {
                            var menu = $(parent.currentTarget).find('.light-popup')
                            menu.show()
                        }, 500);
                    },
                    function(parent) {
                        that.cancelHoverTimer();
                        var menu = $(parent.currentTarget).find('.light-popup')
                        menu.hide()
                        // hide all others too
                        $('.light-popup').hide()
                    });
        },

        cancelHoverTimer: function() {
            if (this.hoverTimer != null) {
                clearTimeout(this.hoverTimer);
                this.hoverTimer = null;
            }
        },

        displayEntity: function(event) {
            if (event.metaKey || event.shiftKey)
                // trying to open in a new tab, do not act on it here!
                return;
            event.preventDefault();
            var $nodeSpan = $(event.currentTarget);
            var $nodeA = $nodeSpan.children('a').first();
            var entityId = $nodeSpan.closest('.tree-box').data("entityId");
            var href = $nodeA.attr('href');
            var tab = (this.detailsView)
                    ? this.detailsView.$el.find(".tab-pane.active").attr("id")
                    : undefined;
            if (href) {
                if (tab) {
                    href = href+"/"+tab;
                    stateId = entityId+"/"+tab;
                    this.preselectTab(tab);
                }
                Backbone.history.navigate(href);
                this.displayEntityId(entityId, $nodeSpan.data("app-id"));
            } else {
                log("no a.href in clicked target");
                log($nodeSpan);
            }
        },

        displayEntityId: function (id, appName, afterLoad) {
            var that = this;
            this.highlightEntity(id);

            var entityLoadFailed = function() {
                return that.displayEntityNotFound(id);
            };

            if (appName === undefined) {
                appName = $("#span-"+id).data("app-id")
            }
            if (appName === undefined) {
                if (!afterLoad) {
                    // try a reload if given an ID we don't recognise
                    this.collection.includeEntities([id]);
                    this.collection.fetch({
                        success: function() { _.defer(function() { that.displayEntityId(id, appName, true); }); },
                        error: function() { _.defer(function() { that.displayEntityId(id, appName, true); }); }
                    });
                    ViewUtils.fadeToIndicateInitialLoad($("div#details"))
                    return;
                } else {
                    // no such app
                    entityLoadFailed();
                    return; 
                }
            }

            var app = new Application.Model();
            var entitySummary = new EntitySummary.Model;

            app.url = "/v1/applications/" + appName;
            entitySummary.url = "/v1/applications/" + appName + "/entities/" + id;

            // in case the server response time is low, fade out while it refreshes
            // (since we can't show updated details until we've retrieved app + entity details)
            ViewUtils.fadeToIndicateInitialLoad($("div#details"));

            $.when(app.fetch(), entitySummary.fetch())
                .done(function() {
                    that.showDetails(app, entitySummary);
                })
                .fail(entityLoadFailed);
        },

        displayEntityNotFound: function(id) {
            $("div#details").html(notFoundTemplate({"id": id}));
            ViewUtils.cancelFadeOnceLoaded($("div#details"))
        },

        treeChange: function(event) {
            var $target = $(event.currentTarget);
            var $treeBox = $target.closest('.tree-box');
            if ($target.hasClass('tr-expand')) {
                this.showChildrenOf($treeBox, false);
            } else if ($target.hasClass('tr-expand-all')) {
                this.showChildrenOf($treeBox, true);
            } else if ($target.hasClass('tr-collapse')) {
                this.hideChildrenOf($treeBox, false);
            } else if ($target.hasClass('tr-collapse-all')) {
                this.hideChildrenOf($treeBox, true);
            } else {
                // default - toggle
                if ($treeBox.children('.node-children').is(':visible')) {
                    this.hideChildrenOf($treeBox, false);
                } else {
                    this.showChildrenOf($treeBox, false);
                }
            }
            // hide the popup menu
            this.cancelHoverTimer();
            $('.light-popup').hide();
            // don't let other events interfere
            return false
        },

        hideChildrenOf: function($treeBox, recurse) {
            var that = this;
            if (recurse) {
                $treeBox.children('.node-children').children().each(function (index, childBox) {
                    that.hideChildrenOf($(childBox), recurse)
                });
            }
            $treeBox.children('.node-children').slideUp(300);
            $treeBox.children('.entity_tree_node_wrapper').find('.tree-node-state').removeClass('icon-chevron-down').addClass('icon-chevron-right');
        },

        showChildrenOf: function($treeBox, recurse) {
            var that = this;
            var idToExpand = $treeBox.children('.entity_tree_node_wrapper').attr('id');
            var model = this.collection.get(idToExpand);
            if (model == null) {
                // not yet loaded; parallel thread should load
                return;
            }
            var children = model.get('children');
            _.each(children, function(child) {
                var id = child.id;
                if (!$('#'+id, that.$el).length)
                    // load, but only if necessary
                    that.updateNode(id, idToExpand) 
            })
            if (this.collection.includeEntities(children)) {
                // we have to load entities before we can proceed
                this.collection.fetch({
                    success: function() {
                        if (recurse) {
                            $treeBox.children('.node-children').children().each(function (index, childBox) {
                                _.defer( function() { that.showChildrenOf($(childBox), recurse) } );
                            });
                        }
                    }
                })
            }
            $treeBox.children('.node-children').slideDown(300);
            $treeBox.children('.entity_tree_node_wrapper').find('.tree-node-state').removeClass('icon-chevron-right').addClass('icon-chevron-down');
            if (recurse) {
                $treeBox.children('.node-children').children().each(function (index, childBox) {
                    that.showChildrenOf($(childBox), recurse);
                })
            }
        },

        /**
         * Causes the tab with the given name to be selected automatically when
         * the view is next rendered.
         */
        preselectTab: function(tab, tabDetails) {
            this.currentTab = tab;
            this.currentTabDetails = tabDetails;
        },

        showDetails: function(app, entitySummary) {
            var that = this;
            ViewUtils.cancelFadeOnceLoaded($("div#details"));

            var whichTab = this.currentTab;
            if (whichTab === undefined) {
                whichTab = "summary";
                if (this.detailsView) {
                    whichTab = this.detailsView.$el.find(".tab-pane.active").attr("id");
                    this.detailsView.close();
                }
            }
            if (this.detailsView) {
                this.detailsView.close();
            }
            this.detailsView = new EntityDetailsView({
                model:entitySummary,
                application:app,
                appRouter:this.options.appRouter,
                preselectTab:whichTab,
                preselectTabDetails:this.currentTabDetails,
            });

            this.detailsView.on("entity.expunged", function() {
                that.preselectTab("summary");
                var id = that.selectedEntityId;
                var model = that.collection.get(id);
                if (model && model.get("parentId")) {
                    that.displayEntityId(model.get("parentId"));
                } else if (that.collection) {
                    that.displayEntityId(that.collection.first().id);
                } else if (id) {
                    that.displayEntityNotFound(id);
                } else {
                    that.displayEntityNotFound("?");
                }
                that.collection.fetch();
            });
            this.detailsView.render( $("div#details") );
        },

        highlightEntity: function(id) {
            if (id) this.selectedEntityId = id;
            else id = this.selectedEntityId;

            $(".entity_tree_node_wrapper").removeClass("active");
            if (id) {
                var $selectedNode = $(".entity_tree_node_wrapper#"+id);
                // make this node active
                $selectedNode.addClass("active");

                // open the parent nodes if needed
                var $nodeToOpenInParent = $selectedNode;
                while ($nodeToOpenInParent.length && !$nodeToOpenInParent.is(':visible')) {
                    $nodeToOpenInParent = $nodeToOpenInParent.closest('.node-children').closest('.tree-box');
                    this.showChildrenOf($nodeToOpenInParent);
                }

                // if we want to auto-expand the children of the selected node:
//              this.showChildrenOf($selectedNode.closest('.tree-box'), false)
            }
        }
    });

    return ApplicationTreeView;
})
