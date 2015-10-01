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
    "model/app-tree", "text!tpl/apps/tree-item.html", "text!tpl/apps/tree-empty.html"
], function (_, $, Backbone, ViewUtils,
             AppTree, TreeItemHtml, EmptyTreeHtml) {

    var emptyTreeTemplate = _.template(EmptyTreeHtml);
    var treeItemTemplate = _.template(TreeItemHtml);

    var findAllTreeboxes = function(id, $scope) {
        return $('.tree-box[data-entity-id="' + id + '"]', $scope);
    };

    var findRootTreebox = function(id) {
        return $('.lozenge-app-tree-wrapper').children('.tree-box[data-entity-id="' + id + '"]', this.$el);
    };

    var findChildTreebox = function(id, $parentTreebox) {
        return $parentTreebox.children('.node-children').children('.tree-box[data-entity-id="' + id + '"]');
    };

    var findMasterTreebox = function(id, $scope) {
        return $('.tree-box[data-entity-id="' + id + '"]:not(.indirect)', $scope);
    };

    var createEntityTreebox = function(id, name, $domParent, depth, indirect) {
        // Tildes in sort key force entities with no name to bottom of list (z < ~).
        var sortKey = (name ? name.toLowerCase() : "~~~") + "     " + id.toLowerCase();

        // Create the wrapper.
        var $treebox = $(
                '<div data-entity-id="'+id+'" data-sort-key="'+sortKey+'" data-depth="'+depth+'" ' +
                'class="tree-box toggler-group' +
                    (indirect ? " indirect" : "") +
                    (depth == 0 ? " outer" : " inner " + (depth % 2 ? " depth-odd" : " depth-even")+
                    (depth == 1 ? " depth-first" : "")) + '">'+
                '<div class="entity_tree_node_wrapper"></div>'+
                '<div class="node-children toggler-target hide"></div>'+
                '</div>');

        // Insert into the passed DOM parent, maintaining sort order relative to siblings: name then id.
        var placed = false;
        var contender = $(".toggler-group", $domParent).first();
        while (contender.length && !placed) {
            var contenderKey = contender.data("sort-key");
            if (sortKey < contenderKey) {
                contender.before($treebox);
                placed = true;
            } else {
                contender = contender.next(".toggler-group", $domParent);
            }
        }
        if (!placed) {
            $domParent.append($treebox);
        }
        return $treebox;
    };

    var getOrCreateApplicationTreebox = function(id, name, treeView) {
        var $treebox = findRootTreebox(id);
        if (!$treebox.length) {
            var $insertionPoint = $('.lozenge-app-tree-wrapper', treeView.$el);
            if (!$insertionPoint.length) {
                // entire view must be created
                treeView.$el.html(
                        '<div class="navbar_main_wrapper treeloz">'+
                        '<div id="tree-list" class="navbar_main treeloz">'+
                        '<div class="lozenge-app-tree-wrapper">'+
                        '</div></div></div>');
                $insertionPoint = $('.lozenge-app-tree-wrapper', treeView.$el);
            }
            $treebox = createEntityTreebox(id, name, $insertionPoint, 0, false);
        }
        return $treebox;
    };

    var getOrCreateChildTreebox = function(id, name, isIndirect, $parentTreebox) {
        var $treebox = findChildTreebox(id, $parentTreebox);
        if (!$treebox.length) {
            $treebox = createEntityTreebox(id, name, $parentTreebox.children('.node-children'), $parentTreebox.data("depth") + 1, isIndirect);
        }
        return $treebox;
    };

    var updateTreeboxContent = function(entity, $treebox, treeView) {
        var $newContent = $(treeView.template({
            id: entity.get('id'),
            parentId:  entity.get('parentId'),
            model: entity,
            statusIconUrl: ViewUtils.computeStatusIconInfo(entity.get("serviceUp"), entity.get("serviceState")).url,
            indirect: $treebox.hasClass('indirect'),
        }));

        var $wrapper = $treebox.children('.entity_tree_node_wrapper');

        // Preserve old display status (just chevron direction at present).
        if ($wrapper.find('.tree-node-state').hasClass('icon-chevron-down')) {
            $newContent.find('.tree-node-state').removeClass('icon-chevron-right').addClass('icon-chevron-down');
        }

        $wrapper.html($newContent);
        addEventsToNode($treebox, treeView);
    };

    var addEventsToNode = function($node, treeView) {
        // show the "light-popup" (expand / expand all / etc) menu
        // if user hovers for 500ms. surprising there is no option for this (hover delay).
        // also, annoyingly, clicks around the time the animation starts don't seem to get handled
        // if the click is in an overlapping reason; this is why we position relative top: 12px in css
        $('.light-popup', $node).parent().parent().hover(
                function(parent) {
                    treeView.cancelHoverTimer();
                    treeView.hoverTimer = setTimeout(function() {
                        var menu = $(parent.currentTarget).find('.light-popup');
                        menu.show();
                    }, 500);
                },
                function(parent) {
                    treeView.cancelHoverTimer();
                    $('.light-popup').hide();
                }
        );
    };

    var selectTreebox = function(id, $treebox, treeView) {
        $('.entity_tree_node_wrapper').removeClass('active');
        $treebox.children('.entity_tree_node_wrapper').addClass('active');

        var entity = treeView.collection.get(id);
        if (entity) {
            treeView.selectedEntityId = id;
            treeView.trigger('entitySelected', entity);
        }
    };


    return Backbone.View.extend({
        template: treeItemTemplate,
        hoverTimer: null,

        events: {
            'click span.entity_tree_node .tree-change': 'treeChange',
            'click span.entity_tree_node': 'nodeClicked'
        },

        initialize: function() {
            this.collection.on('add', this.entityAdded, this);
            this.collection.on('change', this.entityChanged, this);
            this.collection.on('remove', this.entityRemoved, this);
            this.collection.on('reset', this.renderFull, this);
            _.bindAll(this);
        },

        beforeClose: function() {
            this.collection.off("reset", this.renderFull);
        },

        entityAdded: function(entity) {
            // Called when the full entity model is fetched into our collection, at which time we can replace
            // the empty contents of any placeholder tree nodes (.tree-box) that were created earlier.
            // The entity may have multiple 'treebox' views (in the case of group members).

            // If the new entity is an application, we must create its placeholder in the DOM.
            if (!entity.get('parentId')) {
                var $treebox = getOrCreateApplicationTreebox(entity.id, entity.get('name'), this);

                // Select the new app if there's no current selection.
                if (!this.selectedEntityId)
                    selectTreebox(entity.id, $treebox, this);
            }

            this.entityChanged(entity);
        },

        entityChanged: function(entity) {
            // The entity may have multiple 'treebox' views (in the case of group members).
            var that = this;
            findAllTreeboxes(entity.id).each(function() {
                var $treebox = $(this);
                updateTreeboxContent(entity, $treebox, that);
            });
        },

        entityRemoved: function(entity) {
            // The entity may have multiple 'treebox' views (in the case of group members).
            findAllTreeboxes(entity.id, this.$el).remove();
            // Collection seems sometimes to retain children of the removed node;
            // not sure why, but that's okay for now.
            if (this.collection.getApplications().length == 0)
                this.renderFull();
        },

        nodeClicked: function(event) {
            var $treebox = $(event.currentTarget).closest('.tree-box');
            var id = $treebox.data('entityId');
            selectTreebox(id, $treebox, this);
            return false;
        },

        selectEntity: function(id) {
            var $treebox = findMasterTreebox(id, this.$el);
            selectTreebox(id, $treebox, this);
        },

        renderFull: function() {
            var that = this;
            this.$el.empty();

            // Display tree and highlight the selected entity.
            if (this.collection.getApplications().length == 0) {
                this.$el.append(emptyTreeTemplate());

            } else {
                _.each(this.collection.getApplications(), function(appId) {
                    var entity = that.collection.get(appId);
                    var $treebox = getOrCreateApplicationTreebox(entity.id, entity.name, that);
                    updateTreeboxContent(entity, $treebox, that);
                });
            }

            // Select the first app if there's no current selection.
            if (!this.selectedEntityId) {
                var firstApp = _.first(this.collection.getApplications());
                if (firstApp)
                    this.selectEntity(firstApp);
            }
            return this;
        },

        cancelHoverTimer: function() {
            if (this.hoverTimer != null) {
                clearTimeout(this.hoverTimer);
                this.hoverTimer = null;
            }
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
            return false;
        },

        showChildrenOf: function($treeBox, recurse) {
            var $wrapper = $treeBox.children('.entity_tree_node_wrapper');
            var $childContainer = $treeBox.children('.node-children');
            var idToExpand = $treeBox.data('entityId');
            var model = this.collection.get(idToExpand);
            if (model == null) {
                // not yet loaded; parallel thread should load
                return;
            }

            var that = this;
            var children = model.get('children'); // entity summaries: {id: ..., name: ...}
            var renderChildrenAsIndirect = $treeBox.hasClass("indirect");
            _.each(children, function(child) {
                var $treebox = getOrCreateChildTreebox(child.id, child.name, renderChildrenAsIndirect, $treeBox);
                var model = that.collection.get(child.id);
                if (model) {
                    updateTreeboxContent(model, $treebox, that);
                }
            });
            var members = model.get('members'); // entity summaries: {id: ..., name: ...}
            _.each(members, function(member) {
                var $treebox = getOrCreateChildTreebox(member.id, member.name, true, $treeBox);
                var model = that.collection.get(member.id);
                if (model) {
                    updateTreeboxContent(model, $treebox, that);
                }
            });

            if (this.collection.includeEntities(_.union(children, members))) {
                // we have to load entities before we can proceed
                this.collection.fetch({
                    success: function() {
                        if (recurse) {
                            $childContainer.children('.tree-box').each(function () {
                                var $treebox = $(this);
                                _.defer(function() {
                                    that.showChildrenOf($treebox, recurse);
                                });
                            });
                        }
                    }
                });
            }

            $childContainer.slideDown(300);
            $wrapper.find('.tree-node-state').removeClass('icon-chevron-right').addClass('icon-chevron-down');
            if (recurse) {
                $childContainer.children('.tree-box').each(function () {
                    that.showChildrenOf($(this), recurse);
                })
            }
        },

        hideChildrenOf: function($treeBox, recurse) {
            var $wrapper = $treeBox.children('.entity_tree_node_wrapper');
            var $childContainer = $treeBox.children('.node-children');
            if (recurse) {
                var that = this;
                $childContainer.children('.tree-box').each(function () {
                    that.hideChildrenOf($(this), recurse);
                });
            }
            $childContainer.slideUp(300);
            $wrapper.find('.tree-node-state').removeClass('icon-chevron-down').addClass('icon-chevron-right');
        },

    });

});
