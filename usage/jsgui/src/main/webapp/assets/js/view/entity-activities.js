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
            "click button.details":"showFullActivity"
        },
        initialize:function () {
            var that = this
            this.$el.html(this.template({}))
            this.collection.url = this.model.getLinkByName("activities")
            this.collection.fetch()
            this.collection.on("reset", this.render, this)
            this.callPeriodically(function () {
                that.collection.fetch()
            }, 5000)
        },
        beforeClose:function () {
            if (this.detailsModal) this.detailsModal.close()
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
                        cid:task.cid,
                        displayName:task.get("displayName"),
                        submitTimeUtc:task.get("submitTimeUtc"),
                        startTimeUtc:task.get("startTimeUtc"),
                        endTimeUtc:task.get("endTimeUtc"),
                        currentStatus:task.get("currentStatus"),
                        entityDisplayName:task.get("entityDisplayName")
                    }))
            })
            }
            return this
        },
        showFullActivity:function (eventName) {
            var cid = $(eventName.currentTarget).attr("id"),
                task = this.collection.getByCid(cid)
            // clean the old modal view !!
            if (this.detailsModal) this.detailsModal.close()
            this.detailsModal = new ActivitiesView.Modal({
                model:task
            })
            this.$("#activity-modal").html(this.detailsModal.render().el)
            // show the big fat modal
            this.$("#activity-modal .modal").modal("show").css({
                "min-width":500,
                "max-width":940,
                width:function () {
                    return ($(document).width() * .9) + "px";
                },
                "margin-left":function () {
                    return -($(this).width() / 2)
                }
            })
        }
    })

    ActivitiesView.Modal = Backbone.View.extend({
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