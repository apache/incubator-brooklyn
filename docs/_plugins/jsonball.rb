#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#  http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
require 'json'

# JSON parser tag, creating map for use in jekyll markdown

# usage:  {% jsonball varname from TYPE PARAM %}
#
# where TYPE is one of {data,var,file,page}, described below

# drop this into your _plugins/ folder, then you can write, e.g.
#
#   {% jsonball foo from data { "a": "b" } %}
#
# and then later refer to {{ foo.a }} to get b inserted

# more usefully, you can load it from a variable x, e.g.
#   {% capture x %}{% include toc.json %}{% endcapture %}
#
#   {% jsonball foo from var x %}

# even better, to read from a file, say toc.json
# (absolute, or relative to the page being jekylled):
#
#   {% jsonball foo from file toc.json %}
#
# then e.g. {% for record in jsonball %} {{ record.name }} {% endfor %}
# to print out all the name entries (or build a fancy TOC sidebar)

# and finally, if that json file might itself contain liquid tags,
# or need jekylling, treat it as a page and it will get jekylled
# (we use this for toc.json reading from subdirs' toc.json files):
#
#   {% jsonball foo from page toc.json %}

module JekyllJsonball
  class JsonballTag < Liquid::Tag

    def initialize(tag_name, text, tokens)
      super
      @text = text
    end

    def render(context)
	if /(.+) from var (.+)/.match(@text)
		context[$1] = JSON context[$2]
		return ''
	end
	if /(.+) from data (.+)/.match(@text)
		context[$1] = JSON $2
		return ''
	end
	if /(.+) from file (.+)/.match(@text)
		context[$1] = JSON page_relative_file_contents(context, $2.strip)
		return ''
	end
	if /(.+) from page (.+)/.match(@text)
		context[$1] = JSON jekylled_page_relative_file_contents(context, $2.strip)
		return ''
	end
	# syntax error
	return 'ERROR:bad_jsonball_syntax'
    end

    def page_relative_file_contents(context, filename)
	jekyllSite = context.registers[:site]
	dir = jekyllSite.source+'/'+File.dirname(context['page']['url'])
        filename = context[filename] || filename
	if !filename.match(/\/.*/)
		filename = dir + '/' + filename
	end
	file = File.open(filename, "rb")
	return file.read
    end

    def jekylled_page_relative_file_contents(context, filename)
	jekyllSite = context.registers[:site]
        filename = context[filename] || filename
	targetPage = Jekyll::Page.new(jekyllSite, jekyllSite.source, File.dirname(context['page']['url']), filename)
	targetPage.render(jekyllSite.layouts, jekyllSite.site_payload)
	targetPage.output
    end

  end
end

Liquid::Template.register_tag('jsonball', JekyllJsonball::JsonballTag)
