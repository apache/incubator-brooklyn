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

# tag to read and insert a file relative to the current working directory
# (like include, but in the dir where it is invoked)

# there is also readj which reads a file and applies jekyll processing to it
# handy if we want to include a toc.json file which itself calls {% readj child/toc.json %}
# (note however variables do not seem to be exported when use readj (TODO),
# although they are exported if you have _includes/file.md and use the standard include file)

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

