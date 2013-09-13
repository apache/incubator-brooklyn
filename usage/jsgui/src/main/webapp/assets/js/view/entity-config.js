/**
 * Render entity config tab.
 *
 * @type {*}
 */
define([
    "underscore", "jquery", "backbone", "brooklyn-utils",
    "view/viewutils", "model/config-summary", "text!tpl/apps/config.html",
    "jquery-datatables", "datatables-extensions"
], function (_, $, Backbone, Util, ViewUtils, ConfigSummary, ConfigHtml) {

    var EntityConfigView = Backbone.View.extend({
        template:_.template(ConfigHtml),
        configMetadata:{},
        refreshActive:true,
        events:{
            'click .refresh':'refreshNow',
            'click .filterEmpty':'toggleFilterEmpty',
            'click .toggleAutoRefresh':'toggleAutoRefresh'
        },
        initialize:function () {
        	this.$el.html(this.template({ }));
            $.ajaxSetup({ async:true });
            var that = this,
                $table = this.$('#config-table');
            that.table = ViewUtils.myDataTable($table, {
                "fnRowCallback": function( nRow, aData, iDisplayIndex, iDisplayIndexFull ) {
                    $(nRow).attr('id', aData[0])
                    $('td',nRow).each(function(i,v){
                        if (i==1) $(v).attr('class','config-actions');
                        if (i==2) $(v).attr('class','config-value');
                    })
                    return nRow;
                },
                "aoColumnDefs": [
                                 { // name, with tooltip
                                     "mRender": function ( data, type, row ) {
                                         // name (column 1) should have tooltip title
                                         return '<span class="config-name" '+ 
                                             'rel="tooltip" title='+
                                             (data['description'] ? 
                                                     '"<b>'+Util.prep(data['description'])+'</b><br/>' : '')+
                                             '('+Util.prep(data['type'])+')" data-placement="left">'+
                                             Util.prep(data['name'])+'</span>';
                                     },
                                     "aTargets": [ 1 ]
                                 },
                                 { // actions (just one, json link hard coded here apart from url)
                                     "mRender": function ( link, type, row ) {
                                         if (link=="") return "";
                                         var text=""
                                         var icon="icon-file"
                                         var title="JSON direct link"
                                         var actionsText = 
                                             "<a href='"+Util.prep(link)+"'"+
                                             " class='"+Util.prep(icon)+"'"+
                                             " title='"+Util.prep(title)+"'>"+
                                                 Util.prep(text)+"</a>\n";
                                         //just one action here
                                         return actionsText;
                                     },
                                     "aTargets": [ 2 ]
                                 },
                                 { // value
                                     "mRender": function ( data, type, row ) {
                                         return Util.prep(Util.roundIfNumberToNumDecimalPlaces(data, 4))
                                     },
                                     "aTargets": [ 3 ]
                                 },
                                 // ID in column 0 is standard (assumed in ViewUtils)
                                 { "bVisible": false,  "aTargets": [ 0 ] /* hide id column */ }
                             ]            
            });
            ViewUtils.addFilterEmptyButton(that.table);
            ViewUtils.addAutoRefreshButton(that.table);
            ViewUtils.addRefreshButton(that.table);
            that.loadConfigMetadata(that);
            that.updateConfigPeriodically(that);
            that.toggleFilterEmpty();
        },
        render:function () {
            this.updateConfigNow(this);
            return this;
        },
        toggleFilterEmpty:function () {
            ViewUtils.toggleFilterEmpty(this.$('#config-table'), 3);
        },
        toggleAutoRefresh:function () {
            ViewUtils.toggleAutoRefresh(this);
        },
        enableAutoRefresh: function(isEnabled) {
            this.refreshActive = isEnabled
        },
        refreshNow:function () {
            this.updateConfigNow(this);  
        },
        updateConfigPeriodically:function (that) {
            var self = this;
            that.callPeriodically("entity-config", function() {
                if (self.refreshActive)
                    self.updateConfigNow(that);
            }, 3000);
        },
        loadConfigMetadata: function(that) {
            var url =  that.model.getLinkByName('config');
            $.get(url, function (data) {
                for (d in data) {
                    var config = data[d];
                    that.configMetadata[config["name"]] = {
                          name:config["name"],
                          description:config["description"],
                          actionGetData:config["links"]["self"],
                          type:config["type"]
                    }
                }
                that.updateConfigNow(that);
                that.table.find('*[rel="tooltip"]').tooltip();
            });
        },
        updateConfigNow:function (that) {
            var url = that.model.getConfigUpdateUrl(),
                $table = that.$('#config-table');
            if (that.viewIsClosed) {
                return
            }
            $.get(url, function (data) {
                if (that.viewIsClosed) return
                ViewUtils.updateMyDataTable($table, data, function(value, name) {
                    var metadata = that.configMetadata[name]
                    if (metadata==null) {                        
                        // TODO should reload metadata when this happens (new sensor for which no metadata known)
                        // (currently if we have dynamic sensors, their metadata won't appear
                        // until the page is refreshed; don't think that's a bit problem -- mainly tooltips
                        // for now, we just return the partial value
                        return [name, {'name':name}, "", value]
                    } 
                    return [name, metadata,
                        metadata["actionGetData"],
                        value
                    ];
                });
                ViewUtils.processTooltips($table)
            });
        }
    });
    return EntityConfigView;
});
