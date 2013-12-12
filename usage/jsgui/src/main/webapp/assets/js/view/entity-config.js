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
            _.bindAll(this)
        	this.$el.html(this.template({ }));
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
                                                     '"<b>'+Util.escape(data['description'])+'</b><br/>' : '')+
                                             '('+Util.escape(data['type'])+')" data-placement="left">'+
                                             Util.escape(data['name'])+'</span>';
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
                                             "<a href='"+Util.escape(link)+"'"+
                                             " class='"+Util.escape(icon)+"'"+
                                             " title='"+Util.escape(title)+"'>"+
                                                 Util.escape(text)+"</a>\n";
                                         //just one action here
                                         return actionsText;
                                     },
                                     "aTargets": [ 2 ]
                                 },
                                 { // value
                                     "mRender": function ( data, type, row ) {
                                         return Util.toDisplayString(data)
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
            that.loadConfigMetadata();
            that.updateConfigPeriodically();
            that.toggleFilterEmpty();
        },
        render:function () {
            this.updateConfigNow();
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
            this.updateConfigNow();  
        },
        isRefreshActive: function() { return this.refreshActive; },
        updateConfigNow:function () {
            var that = this
            ViewUtils.get(that, that.model.getConfigUpdateUrl(), function(data) { that.updateWithData(data) },
                    { enablement: that.isRefreshActive });
        },
        updateConfigPeriodically:function () {
            var that = this
            ViewUtils.getRepeatedlyWithDelay(that, that.model.getConfigUpdateUrl(), function(data) { that.updateWithData(data) },
                    { enablement: that.isRefreshActive });
        },
        updateWithData: function (data) {
            var that = this
            $table = that.$('#config-table');
            ViewUtils.updateMyDataTable($table, data, function(value, name) {
                var metadata = that.configMetadata[name]
                if (metadata==null) {                        
                    // TODO should reload metadata when this happens (new sensor for which no metadata known)
                    // (currently if we have dynamic sensors, their metadata won't appear
                    // until the page is refreshed; don't think that's a big problem -- mainly tooltips
                    // for now, we just return the partial value
                    return [name, {'name':name}, "", value]
                } 
                return [name, metadata,
                    metadata["actionGetData"],
                    value
                ];
            });
            ViewUtils.processTooltips($table)
        },
        loadConfigMetadata: function() {
            var that = this
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
                that.updateConfigNow();
                that.table.find('*[rel="tooltip"]').tooltip();
            }).fail(that.onConfigMetadataFailure);
        },
        onConfigMetadataFailure: function() {
            log("unable to load config metadata")
            ViewUtils.fadeToIndicateInitialLoad()
        }
    });
    return EntityConfigView;
});
