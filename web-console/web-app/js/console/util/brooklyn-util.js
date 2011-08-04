Brooklyn.util = (function(){

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
        getDataTable: getDataTable,
        getDataTableSelectedRowData: getDataTableSelectedRowData
    };

}());
