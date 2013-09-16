/**
 * Sub-View to render the Application tree.
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone",
    "model/app-tree", "./entity-details", "model/entity-summary", "model/application",
    "text!tpl/apps/tree-item.html", "text!tpl/apps/tree-empty.html", "text!tpl/apps/details.html", "text!tpl/apps/entity-not-found.html"
], function (_, $, Backbone,
             AppTree, EntityDetailsView, EntitySummary, Application,
             TreeItemHtml, TreeEmptyHtml, EntityDetailsEmptyHtml, EntityNotFoundHtml) {

    var treeViewTemplate = _.template(TreeItemHtml),
        notFoundTemplate = _.template(EntityNotFoundHtml);

    var ApplicationTreeView = Backbone.View.extend({
        template: treeViewTemplate,

        events:{
            'click span.entity_tree_node .tree-change':'treeChange',
            'click span.entity_tree_node':'displayEntity'
        },

        initialize:function () {
            this.collection.on('reset', this.render, this)
            _.bindAll(this);
        },

        beforeClose:function () {
            this.collection.off("reset", this.render)
            if (this.detailsView) this.detailsView.close()
        },

        render:function () {
            var that = this
            this.$el.empty()

            // Display tree and highlight the selected entity.
            if (this.collection.isEmpty()) {
                that.$el.append(_.template(TreeEmptyHtml))
            } else {
                that.$el.append(
                        '<div class="navbar_main_wrapper treeloz">'+
                        '<div id="tree-list" class="navbar_main treeloz">'+
                        '<div class="lozenge-app-tree-wrapper">');
                var node = $('div.lozenge-app-tree-wrapper', that.$el);
                
                this.collection.each(function (app) {
                    node.append(that.buildTree(app))
                })
            }
            this.highlightEntity();

            // Render the details for the selected entity.
            if (this.detailsView) {
            	this.detailsView.render()
            } else {
            	// if nothing selected, select the first application
            	if (!this.collection.isEmpty()) {
            		var app0 = this.collection.first().id;
            		_.defer(function () {
            			if (!that.selectedEntityId)
            				that.displayEntityId(app0, app0)
        			});
            	} else {
            	    _.defer(function() {
            	        $("div#details").html(_.template(EntityDetailsEmptyHtml));
            	        $("div#details").find("a[href='#summary']").tab('show')
            	    })
            	}
            }
            return this
        },

        buildTree:function (application) {
            var that = this,
                $template = $(this.template({
                    id:application.get("id"),
                    type:"application",
                    hasChildren: application.hasChildren(),
                    parentApp:application.get("id"),
                    displayName:application.get("name"),
                    iconUrl:application.get("iconUrl"),
                    depth: 0
                })),
                treeFromEntity = function (entity, depth) {
                    var $entityTpl

                    if (entity.hasChildren()) {
                        $entityTpl = $(that.template({
                            id:entity.get("id"),
                            type:"entity",
                            hasChildren: true,
                            parentApp:application.get("id"),
                            displayName:entity.getDisplayName(),
                            iconUrl:entity.get("iconUrl"),
                            depth: depth
                        }))
                        var $parentTpl = $entityTpl.find("#children")
                        _.each(entity.get("children"), function (childEntity) {
                            $parentTpl.append(treeFromEntity(new AppTree.Model(childEntity), depth+1))
                        })
                    } else {
                        $entityTpl = $(that.template({
                            id:entity.get("id"),
                            type:"leaf",
                            hasChildren: false,
                            parentApp:application.get("id"),
                            displayName:entity.getDisplayName(),
                            iconUrl:entity.get("iconUrl"),
                            depth: depth
                        }))
                    }
                    return $entityTpl
                }

            // start rendering from initial children of the application
            var $tree = $template.find("#children")
            _.each(application.get("children"), function (entity) {
                $tree.append(treeFromEntity(new AppTree.Model(entity), 1))
            })
            $('a', $tree).click(function(e) { e.preventDefault(); })
            
            // show the "light-popup" (expand / expand all / etc) menu
            // if user hovers for 500ms. surprising there is no option for this.
            var hoverTimer;
            $('.light-popup', $template).parent().parent().hover(
                    function (parent) {
                        if (hoverTimer!=null) {
                            clearTimeout(hoverTimer);
                            hoverTimer = null;
                        }
                        hoverTimer = setTimeout(function() {
                            var menu = $(parent.currentTarget).find('.light-popup')
                            menu.show()
                        }, 500);
                    },
                    function (parent) {
                        if (hoverTimer!=null) {
                            clearTimeout(hoverTimer);
                            hoverTimer = null;
                        }
                        var menu = $(parent.currentTarget).find('.light-popup')
                        menu.hide()
                        $('.light-popup').hide()
                    })

            return $template
        },

        displayEntity: function(event) {
            event.preventDefault();
            var nodeSpan = $(event.currentTarget)
            var nodeA = $(event.currentTarget).children('a').first()
            var entityId = nodeSpan.attr("id"),
                stateId = entityId,
                href = nodeA.attr('href'),
                tab = (this.detailsView)
                    ? this.detailsView.$el.find(".tab-pane.active").attr("id")
                    : undefined;
            if (href) {
                if (tab) {
                    href = href+"/"+tab
                    stateId = entityId+"/"+tab
                    this.preselectTab(tab)
                }
                window.history.pushState(stateId, "", href)
                this.displayEntityId(entityId, $(event.currentTarget).data("parent-app"));
            } else {
                log("no a.href in clicked target")
                log(nodeSpan)
            }
        },

        displayEntityId:function (id, appName) {
            var that = this;
            console.debug("Displaying entity: " + id);
            this.highlightEntity(id)

            var entityLoadFailed = function() {
                return that.displayEntityNotFound(id);
            };

            if (appName === undefined) {
                appName = $("span.entity_tree_node#"+id).data("parent-app")
            }
            if (appName === undefined) {
                // no such app
                console.error("Couldn't find a parent application for entity: " + id);
                return entityLoadFailed();
            }

            var app = new Application.Model(),
                entitySummary = new EntitySummary.Model;

            app.url = "/v1/applications/" + appName
            entitySummary.url = "/v1/applications/" + appName + "/entities/" + id;

            // in case the server response time is low, fade out while it refreshes
            // (since we can't show updated details until we've retrieved app + entity details)
            $("div#details").fadeTo(1000, 0.3)
            
            $.when(app.fetch(), entitySummary.fetch())
                .done(function() {
                    $("div#details").stop().fadeTo(200, 1)
                    that.showDetails(app, entitySummary);
                })
                .fail(entityLoadFailed);
        },

        displayEntityNotFound: function(id) {
            $("div#details").html(notFoundTemplate({"id": id}));
        },

        treeChange: function(event) {
            log("changing")
            log(event)
            var $target = $(event.currentTarget);
            var $treeBox = $target.closest('.tree-box');
            if ($target.hasClass('tr-expand')) {
                this.showChildrenOf($treeBox, false)
            } else if ($target.hasClass('tr-expand-all')) {
                this.showChildrenOf($treeBox, true)
            } else if ($target.hasClass('tr-collapse')) {
                this.hideChildrenOf($treeBox, false)
            } else if ($target.hasClass('tr-collapse-all')) {
                this.hideChildrenOf($treeBox, true)
            } else {
                // default - toggle
                if ($treeBox.children('#children').is(':visible')) {
                    this.hideChildrenOf($treeBox, false)
                } else {
                    this.showChildrenOf($treeBox, false)
                }
            }
            // hide the popup menu
            $('.light-popup').hide()
            // don't let other events interfere
            return false
        },
        hideChildrenOf: function($treeBox, recurse) {
            var that = this;
            $treeBox.children('#children').slideUp(300)
            $treeBox.children('.tree-node').find('.tree-node-state').removeClass('icon-chevron-down').addClass('icon-chevron-right')
            if (recurse) {
                $treeBox.children('#children').children().each(function (index, childBox) {
                    that.hideChildrenOf($(childBox), recurse)
                })
            }
        },
        showChildrenOf: function($treeBox, recurse) {
            var that = this;
            $treeBox.children('#children').slideDown(300)
            $treeBox.children('.tree-node').find('.tree-node-state').removeClass('icon-chevron-right').addClass('icon-chevron-down')            
            if (recurse) {
                $treeBox.children('#children').children().each(function (index, childBox) {
                    that.showChildrenOf($(childBox), recurse)
                })
            }
        },
        
        /**
         * Causes the tab with the given name to be selected automatically when
         * the view is next rendered.
         */
        preselectTab: function(tab) {
            this.currentTab = tab
        },

        showDetails: function(app, entitySummary) {
            var whichTab = this.currentTab
            if (whichTab === undefined) {
                whichTab = "summary";
                if (this.detailsView) {
                    whichTab = this.detailsView.$el.find(".tab-pane.active").attr("id");
                    this.detailsView.close()
                }
            }
            if (this.detailsView) {
                this.detailsView.close()
            }
            this.detailsView = new EntityDetailsView({
                model:entitySummary,
                application:app
            })
            $("div#details").html(this.detailsView.render().el)
            // preserve the tab selected before
            $("div#details").find('a[href="#'+whichTab+'"]').tab('show');
        },

        highlightEntity:function (id) {
        	if (id) this.selectedEntityId = id
        	else id = this.selectedEntityId
        	$("span.entity_tree_node").removeClass("active")
        	if (id) {
        	    var $selectedNode = $("span.entity_tree_node#"+id);
        		this.showChildrenOf($selectedNode.parents('#app-tree .tree-box'), false)
        		$selectedNode.addClass("active")
        	}
        }
    })

    return ApplicationTreeView
})
