/** Client configuration. */
define({
    "refresh": true,
    "toggleRefresh": function () {
        this.refresh = !this.refresh;
        return this.refresh;
    }
});
