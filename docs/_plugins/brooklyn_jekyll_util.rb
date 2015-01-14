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

# useful tags:

# printing
#     puts message
#     putv variable_to_print_raw
#     putp variable_to_pretty_print

# eval and control flow
#     set_hash_entry hash key variable_to_set  # because sometimes jekyll eval order is different
#     fail message                             # to fail with a message

# stack manipulation:
#     push stack x  # pushs x to stack and clears x
#     pop stack x   # pops from stack into x
# useful e.g. in recursive include calls where x might overwritten

require 'pp'

module BrooklynJekyllUtil
  class PutsTag < Liquid::Tag
    def initialize(tag_name, text, tokens)
      super
      @text = text
    end
    def render(context)
      puts "#{@text}"
    end
  end
  class PutvTag < Liquid::Tag
    def initialize(tag_name, text, tokens)
      super
      @text = text
    end
    def render(context)
      puts "#{@text}: #{context[@text]}"
    end
  end
  class PutpTag < Liquid::Tag
    def initialize(tag_name, text, tokens)
      super
      @text = text
    end
    def render(context)
      puts "#{@text}:"
      PP.pp(context[@text])
      nil
    end
  end
  
  class SetHashEntryTag < Liquid::Tag
    def initialize(tag_name, text, tokens)
      super
      @text = text
    end
    def render(context)
      args = @text.split(/\W+/)
      raise "Need 3 args, the hash, the key, and the var to set" unless args.length == 3
#      puts "getting #{args[0]}['#{args[1]}']"
#      PP.pp(context[args[0]])
#      PP.pp(context[args[0]][args[1]])
      
      context[args[2]] = context[args[0]][args[1]]
      nil
    end
  end

  class FailTag < Liquid::Tag
    def initialize(tag_name, text, tokens)
      super
      @text = text
    end
    def render(context)
      raise "Fail#{@text.length>0 ? ": #{@text}" : ""}"
    end
  end
  
  class PushTag < Liquid::Tag
    def initialize(tag_name, text, tokens)
      super
      @text = text
    end
    def render(context)
      args = @text.split(/\W+/)
      raise "Need 2 args, the stack and the var" unless args.length == 2
      context[args[0]] = [] unless context[args[0]]
      context[args[0]].push(context[args[1]])
      context[args[1]] = nil
    end
  end
  class PopTag < Liquid::Tag
    def initialize(tag_name, text, tokens)
      super
      @text = text
    end
    def render(context)
      args = @text.split(/\W+/)
      raise "Need 2 args, the stack and the var" unless args.length == 2
      context[args[1]] = context[args[0]].pop();
      nil
    end
  end
end

Liquid::Template.register_tag('puts', BrooklynJekyllUtil::PutsTag)
Liquid::Template.register_tag('putv', BrooklynJekyllUtil::PutvTag)
Liquid::Template.register_tag('putp', BrooklynJekyllUtil::PutpTag)
Liquid::Template.register_tag('set_hash_entry', BrooklynJekyllUtil::SetHashEntryTag)
Liquid::Template.register_tag('fail', BrooklynJekyllUtil::FailTag)
Liquid::Template.register_tag('push', BrooklynJekyllUtil::PushTag)
Liquid::Template.register_tag('pop', BrooklynJekyllUtil::PopTag)
