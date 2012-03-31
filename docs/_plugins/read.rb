# tag to read and insert a file relative to the current working directory
# (like include, but in the dir where it is invoked)

# there is also readj which reads a file and applies jekyll processing to it
# handy if we want to include a toc.json file which itself calls {% readj child/toc.json %}

# the argument can be a variable or a filename literal (not quoted)
# TODO: figure out how to accept a quoted string as an argument

module JekyllRead
  class ReadTag < Liquid::Tag
    def initialize(tag_name, text, tokens)
      super
      @text = text
    end
    def render(context)
	jekyllSite = context.registers[:site]
	dir = jekyllSite.source+'/'+File.dirname(context['page']['url'])
	filename = @text.strip
        filename = context[filename] || filename
	if !filename.match(/^\/.*/) 
		filename = dir + '/' + filename
	else
		filename = jekyllSite.source+'/'+filename
	end
	filename = filename.gsub(/\/\/+/,'/')
	file = File.open(filename, "rb")
	return file.read
    end
  end

  class ReadjTag < Liquid::Tag
    def initialize(tag_name, text, tokens)
      super
      @text = text
    end
    def render(context)
	jekyllSite = context.registers[:site]
	filename = @text.strip
	filename = context[filename] || filename
# support vars (above) and relative paths in filename (below - need the right path if there is a subsequent link)
        dir = filename
	if !filename.match(/^\/.*/) 
		dir = File.dirname(context['page']['url']) + '/' + filename
	end
	dir = dir.gsub(/\/\/+/,'/')
	filename = dir.sub(/^.*\//, '')
	dir = dir.gsub(/\/[^\/]*$/, '/')
	targetPage = Jekyll::Page.new(jekyllSite, jekyllSite.source, dir, filename)
	targetPage.render(jekyllSite.layouts, jekyllSite.site_payload)
	targetPage.output
    end
  end
end

Liquid::Template.register_tag('read', JekyllRead::ReadTag)
Liquid::Template.register_tag('readj', JekyllRead::ReadjTag)

