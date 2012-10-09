/**
 * Sub-View to render the Application tree.
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "model/app-tree", "./entity-details", "model/entity-summary",
    "model/application", "text!tpl/apps/tree-item.html"
], function (_, $, Backbone, AppTree, EntityDetailsView, EntitySummary, Application, TreeItemHtml) {

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
            if (this.detailsView) this.detailsView.render()
            return this
        },
        buildTree:function (application) {
            var that = this,
                $template = $(this.template({
                    id:application.get("id"),
                    type:"application",
                    parentApp:null,
                    displayName:application.get("name")
                })), $tree,
                treeFromEntity = function (entity) {
                    var $entityTpl

                    if (entity.hasChildren()) {
                        $entityTpl = $(that.template({
                            id:entity.get("id"),
                            type:"entity",
                            parentApp:application.get("name"),
                            displayName:entity.getDisplayName()
                        }))
                        _.each(entity.get("children"), function (childEntity) {
                            $entityTpl.find("ol.tree").append(treeFromEntity(new AppTree.Model(childEntity)))
                        })
                    } else {
                        $entityTpl = $(that.template({
                            id:entity.get("id"),
                            type:"leaf",
                            parentApp:application.get("name"),
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
            return $template
        },
        displayEntity:function (eventName) {
            var type = $(eventName.currentTarget).data("entity-type"),
                id = $(eventName.currentTarget).attr("id"),
                appName = $(eventName.currentTarget).data("parent-app"),
                entitySummary = new EntitySummary.Model,
                that = this
            var app = new Application.Model()
            app.url = "/v1/applications/" + appName
            app.fetch({async:false})

            entitySummary.url = "/v1/applications/" + appName + "/entities/" + id

            entitySummary.fetch({success:function () {
                if (that.detailsView) that.detailsView.close()
                that.detailsView = new EntityDetailsView({
                    model:entitySummary,
                    application:app
                })
                $("div#details").html(that.detailsView.render().el)
            }})
            return false;
        }
    })

    return ApplicationTreeView
})
