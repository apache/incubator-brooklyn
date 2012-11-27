/**
 * Sub-View to render the Application tree.
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "model/app-tree", "./entity-details", "model/entity-summary",
    "model/application", "text!tpl/apps/tree-item.html", "text!tpl/apps/details.html"
], function (_, $, Backbone, AppTree, EntityDetailsView, EntitySummary, Application, TreeItemHtml, EntityDetailsEmptyHtml) {

    var ApplicationTreeView = Backbone.View.extend({
        tagName:"ol",
        className:"tree applications",
        template:_.template(TreeItemHtml),
        events:{
            'click .name.entity':'displayEntity'
        },
        initialize:function () {
            this.collection.on('reset', this.render, this)
        },
        beforeClose:function () {
            this.collection.off("reset", this.render)
            if (this.detailsView) this.detailsView.close()
        },

        render:function () {
            var that = this
            this.$el.empty()
            this.collection.each(function (app) {
                that.$el.append(that.buildTree(app))
            })
            if (this.collection.size()==0) {
                that.$el.append("<li><i>No applications</i></li>")
            }
            this.highlightEntity();
            if (this.detailsView) {
            	this.detailsView.render()
            } else {
            	// if nothing selected, select the first application
            	if (this.collection.size()>0) {
            		var app0 = this.collection.first().id;
            		_.defer(function () {
            			if (!that.selectedEntityId)
            				that.displayEntityId(app0, app0) 
        			});
            	} else {
            	    _.defer(function() {
            	        $("div#details").html( _.template(EntityDetailsEmptyHtml, {}) )
            	        $("div#details").find("a[href=\"#"+"summary"+"\"]").tab('show')
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
                    parentApp:application.get("id"),
                    displayName:application.get("name")
                })), $tree,
                treeFromEntity = function (entity) {
                    var $entityTpl

                    if (entity.hasChildren()) {
                        $entityTpl = $(that.template({
                            id:entity.get("id"),
                            type:"entity",
                            parentApp:application.get("id"),
                            displayName:entity.getDisplayName()
                        }))
                        var $parentTpl = $entityTpl.find("ol.tree")
                        _.each(entity.get("children"), function (childEntity) {
                            $parentTpl.append(treeFromEntity(new AppTree.Model(childEntity)))
                        })
                    } else {
                        $entityTpl = $(that.template({
                            id:entity.get("id"),
                            type:"leaf",
                            parentApp:application.get("id"),
                            displayName:entity.getDisplayName()
                        }))
                    }
                    return $entityTpl
                }

            // start rendering from initial children of the application
            $tree = $template.find("ol.tree")
            _.each(application.get("children"), function (entity) {
                $tree.append(treeFromEntity(new AppTree.Model(entity)))
            })
            $('a', $tree).click(function(e) { e.preventDefault(); })
            
            return $template
        },
        displayEntity:function (eventName) {
            window.history.pushState($(eventName.currentTarget).attr("id"), "", $('a', $(eventName.currentTarget)).attr('href'));
        	this.displayEntityId($(eventName.currentTarget).attr("id"), $(eventName.currentTarget).data("parent-app"));
        },
        displayEntityId:function (id, appName) {
            var entitySummary = new EntitySummary.Model,
                that = this
            this.highlightEntity(id)

            if (appName === undefined)
                appName = $("span.entity_tree_node#"+id).data("parent-app")
            if (appName === undefined)
                // no such app
                return
            
            var app = new Application.Model()
            app.url = "/v1/applications/" + appName
            app.fetch({async:false})

            entitySummary.url = "/v1/applications/" + appName + "/entities/" + id
            entitySummary.fetch({success:function () {
            	var whichTab="summary";
                if (that.detailsView) {
                	whichTab = $(that.detailsView.el).find(".tab-pane.active").attr("id");
                	that.detailsView.close()
                }
                that.detailsView = new EntityDetailsView({
                    model:entitySummary,
                    application:app
                })
                $("div#details").html(that.detailsView.render().el)
                // preserve the tab selected before
                $("div#details").find("a[href=\"#"+whichTab+"\"]").tab('show')
            }})
            return false;
        },
        highlightEntity:function (id) {
        	if (id) this.selectedEntityId = id
        	else id = this.selectedEntityId
        	$("span.entity_tree_node").removeClass("active")
        	if (id) {
        		$("span.entity_tree_node#"+id).addClass("active")
        	}
        }
    })

    return ApplicationTreeView
})
