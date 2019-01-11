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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.equinox.internal.p2.core.helpers.SecureXMLUtil;
import org.eclipse.equinox.internal.p2.publisher.eclipse.FeatureParser;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.publisher.eclipse.Feature;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.atlassian.bamboo.build.logger.BuildLogger;

import eu.gemtec.packagedrone.deploy.impl.entity.ArtifactType;
import eu.gemtec.packagedrone.deploy.impl.entity.PackageDroneJarArtifact;
import eu.gemtec.packagedrone.deploy.impl.entity.PackageDroneOsgiArtifact;
import eu.gemtec.packagedrone.deploy.impl.entity.PackageDroneJarArtifact.GAV;
import eu.gemtec.packagedrone.deploy.impl.entity.PackageDroneOsgiArtifact.OsgiMetadata;
import eu.gemtec.packagedrone.deploy.impl.upload.UploadClient;

/**
 * Adapts the PackageDrone API to accept normal files instead of artifacts.
 * 
 * @author Veselin Markov
 * @author Peter Jeschke
 */
public class PackageDroneClientAdapter {

	private final UploadClient pdClient;
	private final boolean skipUnparseableFiles;
	private final BuildLogger buildLogger;
	private final String uploadType;

	public PackageDroneClientAdapter(	String host,
										long port,
										String channel,
										String key,
										boolean uploadPoms,
										boolean skipUnparseableFiles,
										String uploadType,
										BuildLogger buildLogger) {
		this.skipUnparseableFiles = skipUnparseableFiles;
		this.buildLogger = buildLogger;
		this.uploadType = uploadType;
		boolean uploadChildArtifacts = UploadTaskConfigurator.CHILD_UPLOAD.equals(uploadType);
		pdClient = new UploadClient(key, channel, host + ":" + port, uploadPoms, uploadChildArtifacts, buildLogger);
	}

	public void uploadFiles(Set<File> filesToUpload) throws ParserConfigurationException, SAXException, IOException, UploadException {
		List<PackageDroneJarArtifact> rootArtifacts = buildArtifactForest(createArtifactsFromFiles(filesToUpload));
		buildLogger.addBuildLogEntry("Uploading artifacts");
		for (PackageDroneJarArtifact rootArtifact : rootArtifacts) {
			buildLogger.addBuildLogEntry("Uploading root artifact: " + rootArtifact);
			pdClient.tryUploadArtifact(rootArtifact, buildLogger);
		}
	}

	/**
	 * Builds a forest of artifact trees. Returns only the roots of these trees. All trees combined
	 * should yield the same count of nodes as the artifacts list.
	 */
	private List<PackageDroneJarArtifact> buildArtifactForest(List<PackageDroneJarArtifact> artifacts) throws IOException {
		List<PackageDroneJarArtifact> roots = new ArrayList<>(artifacts);
		if (UploadTaskConfigurator.NORMAL_UPLOAD.equals(uploadType)) {
			return roots;
		}
		for (PackageDroneJarArtifact artifact : artifacts) {
			List<PackageDroneJarArtifact> children = artifact.findChildren(artifacts);
			switch (uploadType) {
				case UploadTaskConfigurator.CHILD_UPLOAD:
					roots.removeAll(children);
					artifact.getChildren().addAll(children);
					break;
				case UploadTaskConfigurator.DONT_UPLOAD:
					roots.removeAll(children);
					break;
				case UploadTaskConfigurator.NORMAL_UPLOAD:
					throw new RuntimeException("This was different before.");
				default:
					throw new IllegalArgumentException("Uploadtype " + uploadType + " is unknown");
			}
		}
		return roots;
	}

	private List<PackageDroneJarArtifact> createArtifactsFromFiles(Set<File> filesToUpload) throws ParserConfigurationException, SAXException, IOException {
		List<PackageDroneJarArtifact> artifacts = new ArrayList<>();
		for (File file : filesToUpload) {
			buildLogger.addBuildLogEntry("Checking file: " + file.getAbsolutePath());
			GAV gav = getGavFromJar(file, filesToUpload);
			if (gav == null) {
				if (skipUnparseableFiles) {
					buildLogger.addBuildLogEntry("File has no GAV, skipping: " + file.getAbsolutePath());
					continue;
				}
				buildLogger.addErrorLogEntry("File has no GAV, aborting: " + file.getAbsolutePath());
				throw new RuntimeException("File has no GAV: " + file.getAbsolutePath());
			}
			PackageDroneJarArtifact pdArtifact = makeArtifact(file, gav);
			artifacts.add(pdArtifact);
		}
		return artifacts;
	}

	@Nullable
	private GAV getGavFromJar(File file, Set<File> filesToUpload) throws ParserConfigurationException, SAXException, IOException {
		Document doc = findPom(file);
		if (doc == null) {
			return tryGetGavFromParentJar(file, filesToUpload);
		}
		return getGavFromPom(file, doc);
	}

	/**
	 * Tries to read the GAV from a parent JAR (e.g. from myBundle.jar for myBundle-source.jar).
	 */
	@Nullable
	private GAV tryGetGavFromParentJar(File file, Set<File> filesToUpload) throws ParserConfigurationException, SAXException, IOException {
		for (File otherFile : filesToUpload) {
			if (file.equals(otherFile)) {
				continue;
			}
			if (file.getName().startsWith(otherFile.getName().substring(0, otherFile.getName().length() - 4))) {
				return getGavFromJar(otherFile, filesToUpload);
			}
		}
		return null;
	}

	private GAV getGavFromPom(File file, Document doc) throws IOException {
		Node project = doc.getElementsByTagName("project").item(0);
		Node groupId = getChildElementWithTagName(project, "groupId");
		Node artifactId = getChildElementWithTagName(project, "artifactId");
		Node version = getChildElementWithTagName(project, "version");
		Node parent = getChildElementWithTagName(project, "parent");

		if (groupId == null && parent != null) {
			groupId = getChildElementWithTagName(parent, "groupId");
		}
		if (version == null && parent != null) {
			version = getChildElementWithTagName(parent, "version");
		}

		if (groupId == null || artifactId == null || version == null) {
			throw new IOException("POM didn't contain all necessary fields");
		}
		String versionString;
		if (version.getTextContent().contains("SNAPSHOT")) {
			versionString = file.getName().substring(file.getName().indexOf('-') + 1, file.getName().length() - 4);
		} else {
			versionString = version.getTextContent();
		}
		return new GAV(groupId.getTextContent(), artifactId.getTextContent(), versionString);
	}

	@Nullable
	private Node getChildElementWithTagName(Node parent, String tagName) {
		for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
			Node item = parent.getChildNodes().item(i);
			if (tagName.equals(item.getNodeName())) {
				return item;
			}
		}
		return null;
	}

	/**
	 * Tries to find and parse the pom.xml file from the given file.
	 * 
	 * @param file
	 *            a jar-file that contains a pom.xml
	 * @return the parsed document or {@code null}, if no pom is found
	 */
	@Nullable
	private Document findPom(File file) throws ParserConfigurationException, SAXException, IOException, ZipException {
		try (ZipFile jar = new ZipFile(file)) {
			Optional<? extends ZipEntry> pomFile = jar.stream().filter(zEnty -> zEnty.getName().endsWith("pom.xml")).findAny();
			if (pomFile.isPresent()) {
				DocumentBuilderFactory newSecureXMLReader = SecureXMLUtil.newSecureDocumentBuilderFactory();
				newSecureXMLReader.setNamespaceAware(true);

				try (InputStream inputStream = jar.getInputStream(pomFile.get())) {
					DocumentBuilder newSAXParser = newSecureXMLReader.newDocumentBuilder();
					return newSAXParser.parse(inputStream);
				}
			}
		}
		return null;
	}

	public PackageDroneJarArtifact makeArtifact(File file, GAV gav) throws IOException {
		try (JarFile jar = new JarFile(file)) {
			Feature parsedFeature = new FeatureParser().parse(file);

			ArtifactType artifactType = getArtifactType(jar, parsedFeature);
			OsgiMetadata osgiInfo = getOsgiInfo(file, jar, parsedFeature, artifactType);
			if (osgiInfo != null)
				return new PackageDroneOsgiArtifact(file.getAbsolutePath(), gav, osgiInfo);
			return new PackageDroneJarArtifact(file.getAbsolutePath(), gav, artifactType);
		}
	}

	private OsgiMetadata getOsgiInfo(File file, JarFile jar, Feature parsedFeature, ArtifactType artifactType) throws IOException {
		switch (artifactType) {
			case FEATURE:
			case SOURCE_FEATURE:
				return new OsgiMetadata(parsedFeature.getId(), Version.parseVersion(parsedFeature.getVersion()), artifactType);
			case MAVEN_MODULE:
				return null;
			case FRAGMENT:
			case BUNDLE:
			case SOURCE_BUNDLE:
			case SOURCE_FRAGMENT:
			case SOURCE_TEST_FRAGMENT:
			case TEST_FRAGMENT:
				Attributes attributes = jar.getManifest().getMainAttributes();
				String id = attributes.getValue("Bundle-SymbolicName").split(";")[0];
				String version = attributes.getValue("Bundle-Version");
				return new OsgiMetadata(id, Version.parseVersion(version), artifactType);
			case UNDEFINED:
			default:
				throw new RuntimeException("Unknown artifact type: " + file.getAbsolutePath());
		}
	}

	private ArtifactType getArtifactType(JarFile file, Feature feature) throws IOException {
		if (feature == null) {
			Attributes attributes = file.getManifest().getMainAttributes();
			if (attributes.getValue("Bundle-SymbolicName") == null) {
				return ArtifactType.MAVEN_MODULE;
			}
			String osgiId = attributes.getValue("Bundle-SymbolicName").split(";")[0];
			if (attributes.getValue("Fragment-Host") != null) {
				if (osgiId.endsWith(".source")) {
					if (osgiId.endsWith(".test.source") || osgiId.endsWith(".tests.source")) {
						return ArtifactType.SOURCE_TEST_FRAGMENT;
					}
					return ArtifactType.SOURCE_FRAGMENT;
				}
				if (osgiId.endsWith(".test") || osgiId.endsWith(".tests")) {
					return ArtifactType.TEST_FRAGMENT;
				}
				return ArtifactType.FRAGMENT;
			}
			if (osgiId.endsWith(".source")) {
				return ArtifactType.SOURCE_BUNDLE;
			}
			return ArtifactType.BUNDLE;
		}
		if (feature.getId().endsWith(".source")) {
			return ArtifactType.SOURCE_FEATURE;
		}
		return ArtifactType.FEATURE;
	}
}
