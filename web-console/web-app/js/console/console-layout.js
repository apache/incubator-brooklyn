   /*
    * THIS SCRIPT FILE DEFINES LAYOUT FOR THE PAGE AND CONTAINER BEHAVIOUR
    *
    * So when the page loads we set the layouts for both the seperate containers.
    * one layout variable will be for header and footer and the other for the inner container.
    *
    */
    var outerLayout, innerLayout;

    $(document).ready(function () {
     <!-- CREATE TWO LAYOUT OBJECTS AND THEN GIVE THEM SETTINGS WHICH ARE DEFINED BELOW -->
        outerLayout=$("body").layout( outerLayoutSettings );
        innerLayout=$("body > .ui-layout-center").layout( innerLayoutSettings );

    });

    var outerLayoutSettings = {

    applyDefaultStyles:   true

    }

    var innerLayoutSettings = {

    applyDefaultStyles:   true

    }