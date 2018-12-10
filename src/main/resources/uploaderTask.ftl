<!--
   Copyright (c) 2018 Veselin Markov. All rights reserved.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->
[#-- View for Uploader configuration --]
[@ww.textfield name="host"       labelKey="pd.deploy.view.host"     required="true"  /]
[@ww.textfield name="port"       labelKey="pd.deploy.view.port"     required="true" /]
[@ww.textfield name="channel"    labelKey="pd.deploy.view.channel"  required="true" cssClass="long-field" /]
[@ww.textfield name="key"        labelKey="pd.deploy.view.key"      required="true" cssClass="long-field" /]
[@ww.checkbox  name="uploadPom"  labelKey="pd.deploy.view.uploadPom"/]
[@ww.checkbox  name="uploadPom"  labelKey="pd.deploy.view.skipUnparseable"/]
[@ww.select name="artifactToScp" labelKey="pd.deploy.view.artifact" 
			list=artifactsToScp
			listKey="value"
			listValue="displayName"
			groupBy="group"
			toggle = true /]
