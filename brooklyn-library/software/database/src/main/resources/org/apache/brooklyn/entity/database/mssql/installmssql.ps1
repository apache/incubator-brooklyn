[#ftl]
#!ps1
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

$Url = "${config['mssql.download.url']}"
$Path = "C:\sql2008.iso"
$Username = "${config['mssql.download.user']}"
$Password = '${config['mssql.download.password']}'


$WebClient = New-Object System.Net.WebClient
$WebClient.Credentials = New-Object System.Net.Networkcredential($Username, $Password)
$WebClient.DownloadFile( $url, $path )

$mountResult = Mount-DiskImage $Path -PassThru
$driveLetter = (($mountResult | Get-Volume).DriveLetter) + ":\"

New-Item -ItemType Directory -Force -Path "C:\Program Files (x86)\Microsoft SQL Server\DReplayClient\ResultDir"
New-Item -ItemType Directory -Force -Path "C:\Program Files (x86)\Microsoft SQL Server\DReplayClient\WorkingDir"

Install-WindowsFeature NET-Framework-Core

$pass = '${attribute['windows.password']}'
$secpasswd = ConvertTo-SecureString $pass -AsPlainText -Force
$mycreds = New-Object System.Management.Automation.PSCredential ($($env:COMPUTERNAME + "\Administrator"), $secpasswd)

$process = Start-Process ( $driveLetter + "setup.exe") -ArgumentList "/ConfigurationFile=C:\ConfigurationFile.ini" -Credential $mycreds -RedirectStandardOutput "C:\sqlout.txt" -RedirectStandardError "C:\sqlerr.txt" -Wait -PassThru
exit $process.ExitCode
