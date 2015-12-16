#!/usr/bin/env ruby_executable_hooks

# supports --disable_external, --ignore-v-refs, --v-only

require 'html/proofer'

HTML::Proofer.new("./_site", {
  :href_ignore => [
      /https?:\/\/127.*/,
      /https?:\/\/github.com\/apache\/incubator-brooklyn\/edit.*/,
      ((ARGV.include? "--ignore-v-refs") ? /.*\/v\/.*/ : /ignore/),
      ((ARGV.include? "--v-only") ? /\/(|[^v].*|.[^\/].*)/ : /ignore/)
      ],
  :alt_ignore => [/.*/], 
  # don't scan javadoc files (too many errors) 
  # or autogen catalog items (their style files are wrong in some modes; reinstate when cleaner)
  :disable_external => (ARGV.include? "--disable_external"),
  :file_ignore => [ /.*\/(javadoc|apidoc|learnmore\/catalog)\/.*/ ] 
  # bug - must do above - see https://github.com/gjtorikian/html-proofer/issues/145 
#  :file_ignore => [ /.*\/javadoc\/.*/, /.*\/learnmore\/catalog\/.*/ ]
  }).run
