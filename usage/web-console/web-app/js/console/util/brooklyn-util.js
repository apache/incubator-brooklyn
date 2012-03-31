Brooklyn.util = (function(){

    function pauseUpdate(tab){
        $(Brooklyn.eventBus).unbind("update", tab.handler.update);
        setTimeout(function () {rebindUpdate(tab)}, 15000);
    }

    function rebindUpdate(tab){
        tab.handler.update();
        $(Brooklyn.eventBus).bind("update", tab.handler.update);
    }

    function typeTest(a, b) {
        return (typeof a == typeof b)
    }

    // Object equality test based on code in StackOverflow answer by Jean Vincent
    // http://stackoverflow.com/a/6713782/41195
    function testEquivalent(x, y) {
        // if both x and y are null or undefined and exactly the same
        if ( x === y ) return true;

        // if they are not strictly equal, they both need to be Objects
        if ( ! ( x instanceof Object ) || ! ( y instanceof Object ) ) return false;

        // they must have the exact same prototype chain, the closest we can do is
        // test there constructor.
        if ( x.constructor !== y.constructor ) return false;

        for ( var p in x ) {
            // other properties were tested using x.constructor === y.constructor
            if ( ! x.hasOwnProperty( p ) ) continue;

            // allows to compare x[ p ] and y[ p ] when set to undefined
            if ( ! y.hasOwnProperty( p ) ) return false;

            // if they have the same strict value or identity then they are equal
            if ( x[ p ] === y[ p ] ) continue;

            // Numbers, Strings, Functions, Booleans must be strictly equal
            if ( typeof( x[ p ] ) !== "object" ) return false;

            // Objects and Arrays must be tested recursively
            if ( ! testEquivalent2( x[ p ],  y[ p ] ) ) return false;
        }

        for ( p in y ) {
            // allows x[ p ] to be set to undefined
            if ( y.hasOwnProperty( p ) && ! x.hasOwnProperty( p ) ) return false;
        }
        return true;
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
