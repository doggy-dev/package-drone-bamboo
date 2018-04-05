/*
 * Copyright (c) 2018 Veselin Markov. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.gemtec.packagedrone.deploy.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.equinox.internal.p2.core.helpers.SecureXMLUtil;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import eu.gemtec.packagedrone.deploy.impl.entity.PackageDroneArtifact;
import eu.gemtec.packagedrone.deploy.impl.entity.Type;
import eu.gemtec.packagedrone.deploy.impl.entity.PackageDroneArtifact.GAV;
import eu.gemtec.packagedrone.deploy.impl.upload.UploadClient;

/**
 * @author Veselin Markov
 *
 */
public class PackageDroneClientAdapter {

	final UploadClient pdClient;

	public PackageDroneClientAdapter(String host, long port, String channel, String key, boolean uploadPoms) {
		pdClient = new UploadClient(key, channel, host + ":" + port, uploadPoms);
	}

	public void uploadFiles(Set<File> filesToUpload) {
		List<PackageDroneArtifact> features = new LinkedList<>();
		List<PackageDroneArtifact> bundles = new LinkedList<>();
		for (File file : filesToUpload) {
			try {
				GAV gav = getGav(file);
				if (gav == null) {
					// TODO Auto-generated catch block
					continue;
				}
				PackageDroneArtifact pdArtifact = UploadClient.makeAtrifact(file, gav);

				Type artifactType = pdArtifact.getOsgi().getType();
				if (artifactType == Type.Feature)
					features.add(pdArtifact);
				else if (artifactType == Type.Bundle || artifactType == Type.Fragment)
					bundles.add(pdArtifact);

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		ArrayList<PackageDroneArtifact> featuresCopy = new ArrayList<>(features);
		for (PackageDroneArtifact pdArtifact : featuresCopy) {
			try {
				pdClient.tryUpload(pdArtifact, features, bundles);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Nullable
	private GAV getGav(File file) {
		Document doc = null;
		try (ZipFile jar = new ZipFile(file)) {
			Optional<? extends ZipEntry> zipEntry = jar.stream().filter(zEnty -> zEnty.getName().endsWith("pom.xml")).findAny();
			if (zipEntry.isPresent()) {
					DocumentBuilderFactory newSecureXMLReader = SecureXMLUtil.newSecureDocumentBuilderFactory();
					newSecureXMLReader.setNamespaceAware(true);

					try (InputStream inputStream = jar.getInputStream(zipEntry.get())) {
						DocumentBuilder newSAXParser = newSecureXMLReader.newDocumentBuilder();
						doc = newSAXParser.parse(inputStream);
					} catch (SAXException | ParserConfigurationException e2) {
						// TODO Auto-generated catch block
						e2.printStackTrace();
						return null;
					}
			} else {
				// TODO Auto-generated catch block
			}
		} catch (ZipException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

		if (doc == null)
			return null;

		Node project = doc.getElementsByTagName("project").item(0);
		Node groupId = null;
		Node artifactId = null;
		Node version = null;
		Node parent = null;

		Node projectSubelement = project.getFirstChild();
		while (projectSubelement != null && (groupId == null || artifactId == null || version == null)) {
			if ("groupId".equals(projectSubelement.getLocalName())) {
				groupId = projectSubelement;
			} else if ("artifactId".equals(projectSubelement.getLocalName())) {
				artifactId = projectSubelement;
			} else if ("version".equals(projectSubelement.getLocalName())) {
				version = projectSubelement;
			} else if ("parent".equals(projectSubelement.getLocalName())) {
				parent = projectSubelement;
			}
			projectSubelement = projectSubelement.getNextSibling();
		}

		if (groupId == null && parent != null) {
			Node parentSubelement = parent.getFirstChild();
			while (groupId == null && parentSubelement != null) {
				if ("groupId".equals(parentSubelement.getLocalName()))
					groupId = parentSubelement;
				parentSubelement = parentSubelement.getNextSibling();
			}
		}

		if (version == null && parent != null) {
			Node parentSubelement = parent.getFirstChild();
			while (version == null && parentSubelement != null) {
				if ("version".equals(parentSubelement.getLocalName()))
					version = parentSubelement;
				parentSubelement = parentSubelement.getNextSibling();
			}
		}
		return new GAV(groupId.getTextContent(), artifactId.getTextContent(), version.getTextContent());
	}
}
