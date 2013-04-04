define([
    "router"
], function (Router) {

    var View = Backbone.View.extend({
        render:function () {
            this.$el.html("<p>fake view</p>")
            return this
        }
    })

    describe("router", function () {
        var view, router

        beforeEach(function () {
            view = new View
            router = new Router
            $("body").append('<div id="container"></div>')
        })

        afterEach(function () {
            $("#container").remove()
        })

        it("shows the view inside div#container", function () {
            expect($("body #container").length).toBe(1)
            expect($("#container").text()).toBe("")
            router.showView("#container", view)
            expect($("#container").text()).toBe("fake view")
        })

        it("should call 'close' of old views", function () {
            spyOn(view, "close")

            router.showView("#container", view)
            expect(view.close).not.toHaveBeenCalled()
            // it should close the old view
            router.showView("#container", new View)
            expect(view.close).toHaveBeenCalled()
        })
    })
})