require 'digest/md5'
require 'open-uri'

#
# Initial implementation
# http://stackoverflow.com/questions/24153610/category-hierarchy-in-jekyll
#
module Jekyll
  # Add accessor for directory
  class Page
    attr_reader :dir
  end

  class NavTree < Liquid::Tag
    def render(context)
      allowed_roots = ['/guide']
      site = context.registers[:site]
      @page_url = context.environments.first["page"]["url"]
      # @folder_weights = site.data['folder_weights']
      # @folder_icons = site.data['folder_icons']["icons"]
      @nodes = {}

      site.pages.each do |page|
        # exclude all pages that are hidden in front-matter
        # if page.data["navigation"]["show"] != false
        if allowed_roots.any? { |root| page.url.start_with? root }
          path = page.url
          path = path.index('/')==0 ? path[1..-1] : path
          @nodes[path] = page.data
        end
      end

      tree_nodes = {}
      @nodes.each do |path, node|
        current  = tree_nodes
        path.split("/").inject("") do |sub_path,dir|
          sub_path = File.join(sub_path, dir)
          current[sub_path] ||= {}
          current    = current[sub_path]
          sub_path
        end
      end

      puts "generating nav tree for #{@page_url}"
      @nav_location = ''
      begin
      files_first_traverse map_tree_nodes_to_weight(tree_nodes), 0
      rescue Exception => e
        puts [e.message, *e.backtrace].join("\n")
      end
    end

    def files_first_traverse(tree_nodes, depth)
      output = ""
      if depth == 0
        id = 'id="nav-menu"'
      end
      output += "<ul #{id} class=\"nav nav-list\">"

      sorted_tree_nodes = tree_nodes.to_a.sort_by do |(path, node)|
        node[:weight] || 0
      end

      sorted_tree_nodes.each do |(base, node)|
        subtree = node[:subtree]

        name = base[1..-1]
        if name.index('.') != nil
          icon_name = @nodes[name]["icon"]
          name = @nodes[name]["title"]
        end

        li_class = ""
        if base == @page_url
          li_class = "active list-group-item"
          if icon_name
            icon_name = icon_name + " icon-white"
          end
        end

        icon_html = "<span class=\"#{icon_name}\"></span>" unless icon_name.nil?

        output += "<li><a class=\"#{li_class}\" href=\"#{URI::encode base}\">#{icon_html}#{name}</a></li>" if subtree.empty?
      end

      sorted_tree_nodes.each do |base, node|
        subtree = node[:subtree]

        next if subtree.empty?

        href = base
        name = base[1..-1]
        if name.index('.') != nil
          is_parent = false
          name = @nodes[name]["title"]
        else
          is_parent = true
          href = base + '/index.html'
          if name.index('/')
            name = name[name.rindex('/')+1..-1]
          end
        end

        name.gsub!(/_/,' ')

        li_class = ''

        if href == @page_url
          li_class = "active list-group-item"
        end

        if is_parent
          if @page_url.index(base)
            open = true
            list_class = "collapsibleListOpen"
          else
            open = false
            list_class = "collapsibleListClosed"
          end

          id = Digest::MD5.hexdigest(base)

          icon_name = nil # @folder_icons[base]

          icon_html = icon_name.nil? ? "" : "<span class=\"#{icon_name}\"></span>"
          if index_html = @nodes["#{base[1..-1]}/index.html"]
            title = index_html['title']
            li = "<li id=\"node-#{id}\" class=\"parent #{list_class}\"><div class=\"menu-item\">"+
                   "<span class=\"subtree-name\"></span>"+
                   "<a href=\"#{index_html['url']}\" class=\"#{li_class}\">#{title}</a></div>"
          else
            title = name.capitalize
            li = "<li id=\"node-#{id}\" class=\"parent #{list_class}\"><div class=\"menu-item\"><span class=\"subtree-name\">#{icon_html}</span> #{title}</div>"
          end
          @nav_location += " > #{title}"
        else
          icon_name = @nodes[name]["icon"]

          if icon_name && li_class=="active"
            icon_name = icon_name + " icon-white"
          end

          icon_html = icon_name.nil? ? "<i class=\"#{icon_name}\"></i>" : ""
          li = "<li><a class=\"#{li_class}\" href=\"#{URI::encode href}\">#{icon_html}#{name}</a></li>"
        end

        output += li

        subtree_nodes = subtree.reject { |k| k.end_with? 'index.html' }

        depth = depth + 1
        output += files_first_traverse(map_tree_nodes_to_weight(subtree_nodes), depth)

        if is_parent
          output+= "</li>"
        end
      end

      output += "</ul>"
      output
    end

    def map_tree_nodes_to_weight(tree_nodes)
      tree_nodes.each do |base, subtree|
        if base.end_with? '.html'
          weight = -(@nodes[base[1..-1]]['weight'] || 0)
        else
          index_key = "#{base}/index.html"
          weight = subtree[index_key] ? -(@nodes[index_key[1..-1]]['weight'] || 0) : 0
        end
        tree_nodes[base] = {weight: weight, subtree: subtree}
      end
      tree_nodes
    end
  end
end

Liquid::Template.register_tag("dir_navigation", Jekyll::NavTree)
