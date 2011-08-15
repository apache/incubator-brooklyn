Brooklyn.util = (function(){

    function pauseUpdate(tab){
        $(Brooklyn.eventBus).unbind("update", tab.handler.update);
        setTimeout(function () {rebindUpdate(tab)}, 20000);
        console.log("Updating paused")
    }

    function rebindUpdate(tab){
        tab.handler.update();
        $(Brooklyn.eventBus).bind("update", tab.handler.update);
        console.log("Updating resumed")
    }

    function typeTest(a, b) {
        return (typeof a == typeof b)
    }

    function testEquivalent(a, b) {
        var result = true;
        if (!typeTest(a, b)) return false;
        if (typeof a == 'object') {
            for (var p in a) {
                result = testEquivalent(a[p], b[p]);
                if (!result) return false;
            }
            for (var p in b) {
                result = testEquivalent(b[p], a[p]);
                if (!result) return false;
            }
            return result;
        }
        return (a == b);
    }

/* DATATABLES UTIL*/
// NOTE: You can simply call getDataTable(id) once the table is initialized

    function getDataTable(id, sAjaxDataProp, aoColumns, clickCallback, data, paginate) {
        var table = $(id).dataTable( {
                "bRetrieve": true, // return existing table if initialized
                "bAutoWidth": false,
                "bLengthChange": false,
                "bJQueryUI": true,
                "bPaginate": false,
                "bStateSave": true,
                "bDeferRender": true,
                "sAjaxDataProp": sAjaxDataProp,
                "aoColumns": aoColumns
        });
        
        if (clickCallback) {
            $(id + " tbody").click(clickCallback);
        }

        if (data) {
            table.fnClearTable();
            table.fnAddData(data);
        }

        return table;
    }

    function getDataTableSelectedRow(id, event) {
        var table = getDataTable(id);
        var settings = table.fnSettings().aoData;
        return settings[table.fnGetPosition(event.target.parentNode)];
    }

    function getDataTableSelectedRowData(id, event) {
        var row = getDataTableSelectedRow(id, event);
        // TODO bit hacky!
        if (row) {
            return row._aData;
        } else {
            return {};
        }
    }

    return {
        pauseUpdate: pauseUpdate,
        getDataTable: getDataTable,
        getDataTableSelectedRowData: getDataTableSelectedRowData,
        testEquivalent: testEquivalent
    };

}());
