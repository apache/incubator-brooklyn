# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

$Path="C:\InstallTemp"
New-Item -ItemType Directory -Force -Path $Path

$Url = "${config['installer.download.url']}"
$Dl = [System.IO.Path]::Combine($Path, "installer.msi")

$WebClient = New-Object System.Net.WebClient

$WebClient.DownloadFile($Url, $Dl)

$pass = '${attribute['windows.password']}'
$secpasswd = ConvertTo-SecureString $pass -AsPlainText -Force
$mycreds = New-Object System.Management.Automation.PSCredential ($($env:COMPUTERNAME + "\Administrator"), $secpasswd)

Invoke-Command -ComputerName localhost -credential $mycreds -scriptblock {
    param($Dl)
    Start-Process "msiexec" -ArgumentList '/qn','/i',$Dl,'/L c:\7ziplog.txt' -Wait
} -Authentication CredSSP -argumentlist $Dl
