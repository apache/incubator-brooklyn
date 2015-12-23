#ruby

require 'CSV'
require 'github_api'

gh = Github.new

CSV.open("pr_report.tsv", "wb", { :col_sep => "\t" }) do |csv|
  gh.pull_requests.list('apache', 'incubator-brooklyn').
      select { |pr| pr.state == "open" }.
      each { |pr| csv << [ pr.number, pr.title, pr.created_at, pr.user.login ] }
end
