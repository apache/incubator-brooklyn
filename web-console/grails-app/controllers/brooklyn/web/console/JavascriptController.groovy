package brooklyn.web.console

class JavascriptController {

    def index

    def jsTreeConfig = {
        render(contentType:"text/javascript", template:"jsTreeConfig")
    }

    def effectorsConfig = {
        render(contentType:"text/javascript", template:"effectorsConfig")
    }
}
