#!/usr/bin/env bash
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
# chkconfig: - 80 20
#
### BEGIN INIT INFO
# Provides:          ${service.name}
# Required-Start:    $network $syslog
# Required-Stop:     $network $syslog
# Default-Start:
# Default-Stop:
# Short-Description: Brooklyn entity service
# Description:       Service for Brooklyn managed entity
### END INIT INFO

case $1 in
 start)
  touch ${service.log_path}/${service.name}.log
  chown ${service.user} ${service.log_path}/${service.name}.log
  sudo -u ${service.user} ${service.launch_script} >> ${service.log_path}/${service.name}.log 2>&1
  ;;
# stop)
#  ;;
# restart)
#  ;;
# status)
#  ;;
# reload)
#  ;;
 *)
#  echo "Usage: $0 {start|stop|restart|reload|status}"
  echo "Usage: $0 {start}"
  exit 2
  ;;
esac
