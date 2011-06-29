function initTabs() {
    $("#sensors").html("Oh what lovely sensor data I see.");

    $("#tabs").tabs({
        show: function (event, ui) {
            setAccordionLayout(ui);
        }
    });

    $("#summary").accordion();
}

function setAccordionLayout(accordion) {
    $("#summary").css('width', '300px');
}
