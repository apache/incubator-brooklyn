Brooklyn.effectors = (function() {
    var selectedRowData;

    function getTable() {
        return $('#effector-data').dataTable( {
                "bRetrieve": true, // return existing table if initialized
                "bAutoWidth": false,
                "bLengthChange": false,
                "bJQueryUI": true,
                "bPaginate": false,
                "bDeferRender": true,
                "sAjaxDataProp": ".",
                "aoColumns": [
                    { "mDataProp": "name", "sTitle": "name", "sWidth":"100%"  },
                ]
        });
    }

    function _updateEffectorsList(json) {
        var _jsonObject = json;
        getTable().fnClearTable(false);
        getTable().fnAddData(_jsonObject);

        $('#effector-args').empty();
        var noSelectedEffector = document.createElement("p");
        noSelectedEffector.textContent = "Please select an effector to invoke";
        $('#effector-args').append(noSelectedEffector);

        $('#effectors-invoke-button').attr("disabled", "disabled");

        $("#effector-data tbody").click(updateParameters);
        $('#effector-invoke').submit(invokeEffector);
    }

    function updateParameters(event) {
        var settings = getTable().fnSettings().aoData;
        var selectedRow = settings[getTable().fnGetPosition(event.target.parentNode)];
        for(row in settings) {
       		$(settings[row].nTr).removeClass('row_selected');
   		}
 		$(selectedRow.nTr).addClass('row_selected');

        // TODO bit hacky!
        selectedRowData = selectedRow._aData;

        $('#effectors-invoke-button').removeAttr("disabled")

        $('#effector-args').empty();

        var title = document.createElement("p");
        title.textContent = selectedRowData.description;
        $('#effector-args').append(title);

        if(selectedRowData.parameters.length == 0 ) {
            var argumentLabel = document.createElement('p');
            argumentLabel.textContent = "No arguments needed:";
            $('#effector-args').append(argumentLabel);
        } else {
            for (parameter in selectedRowData.parameters){
                var textBox = document.createElement("input");
                var argumentLabel = document.createElement('label');
                var div = document.createElement('div');
                var parameterName = selectedRowData.parameters[parameter].name

                argumentLabel.setAttribute("name", parameterName + "Label");
                argumentLabel.setAttribute("for", parameterName + "Input");
                argumentLabel.textContent = parameterName + ":";
                div.appendChild(argumentLabel);

                textBox.setAttribute("name", parameterName + "Input");
                div.appendChild(textBox);

                $('#effector-args').append(div);
            }
        }
    }

    function updateList(e, entity_id) {
        if (typeof entity_id === 'undefined') {
            return;
        }
        // TODO: Handle failure
        $.getJSON("effectors?id=" + entity_id, _updateEffectorsList);
    }

    function invokeEffector(event){
        //TODO: use form object correctly
        alert('Effector: "' + selectedRowData.name + '" invoked');
        return false;
    }

    function init() {
        $(Brooklyn.eventBus).bind("entity_selected", updateList);
    }

    return {
        init: init,
    };

})();

$(document).ready(Brooklyn.effectors.init);
