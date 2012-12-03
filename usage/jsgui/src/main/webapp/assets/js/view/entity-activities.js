/**
 * Displays the list of activities/tasks the entity performed.
 */
define([
    "underscore", "backbone", "text!tpl/apps/activities.html", "text!tpl/apps/activity-row.html",
    "text!tpl/apps/activity-details.html", "bootstrap", "formatJson"
], function (_, Backbone, ActivitiesHtml, ActivityRowHtml, ActivityDetailsHtml) {

    var ActivitiesView = Backbone.View.extend({
        template:_.template(ActivitiesHtml),
        taskRow:_.template(ActivityRowHtml),
        events:{
            "click #activities-table tr":"rowClick"
        },
        initialize:function () {
            var that = this
            this.$el.html(this.template({}))
            this.collection.url = this.model.getLinkByName("activities")
            this.collection.fetch()
            this.collection.on("reset", this.render, this)
            this.callPeriodically("entity-activities", function () {
                that.collection.fetch()
            }, 5000)
        },
        beforeClose:function () {
            this.collection.off("reset", this.render)
        },
        render:function () {
            var that = this,
                $tbody = this.$("#activities-table tbody").empty()
            if (this.collection.length==0) {
                this.$(".has-no-activities").show();
            } else {                
                this.$(".has-no-activities").hide();
                this.collection.each(function (task) {
                    $tbody.append(that.taskRow({
                        cid:task.get("id"),
                        displayName:task.get("displayName"),
                        submitTimeUtc:task.get("submitTimeUtc"),
                        startTimeUtc:task.get("startTimeUtc"),
                        endTimeUtc:task.get("endTimeUtc"),
                        currentStatus:task.get("currentStatus"),
                        entityDisplayName:task.get("entityDisplayName")
                    }))
                if (that.activeTask) {
                    $("#activities-table tr[id='"+that.activeTask+"']").addClass("selected")
                    that.showFullActivity(that.activeTask)
                }
            })
            }
            return this
        },
        rowClick: function(evt) {
            var row = $(evt.currentTarget).closest("tr")
            var id = row.attr("id")
            $("#activities-table tr").removeClass("selected")
            if (this.activeTask == id) {
                // deselected
                this.activeTask = null
                this.$("#activity-details").hide(100)
            } else {
                row.addClass("selected")
                this.activeTask = id
                this.showFullActivity(id)
            }
        },
        showFullActivity:function (id) {
            var task = this.collection.get(id)
            if (task==null) {
                this.activeTask = null
                this.$("#activity-details").hide(100)                
            }
            var html = _.template(ActivityDetailsHtml, {
                displayName:this.model.get("displayName"),
                description:FormatJSON(task.toJSON())                
            })
            this.$("#activity-details").html(html)
            this.$("#activity-details").show(100)
        }
    })

    ActivitiesView.Details = Backbone.View.extend({
        tagName:"div",
        className:"modal hide",
        template:_.template(ActivityDetailsHtml),
        render:function () {
            this.$el.html(this.template({
                displayName:this.model.get("displayName"),
                description:FormatJSON(this.model.toJSON())
            }))
            return this
        }
    })

    return ActivitiesView
})