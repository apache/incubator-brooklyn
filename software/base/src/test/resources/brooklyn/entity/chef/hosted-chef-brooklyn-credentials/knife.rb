current_dir = File.dirname(__FILE__)
log_level                :info
log_location             STDOUT
node_name                "brooklyn-tests"
client_key               "#{current_dir}/brooklyn-tests.pem"
validation_client_name   "brooklyn-validator"
validation_key           "#{current_dir}/brooklyn-validator.pem"
chef_server_url          "https://api.opscode.com/organizations/brooklyn"
