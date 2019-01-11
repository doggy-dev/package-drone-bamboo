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
package eu.gemtec.packagedrone.deploy.impl.upload;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import javax.ws.rs.core.MediaType;

import org.eclipse.equinox.internal.p2.publisher.eclipse.FeatureParser;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.publisher.eclipse.Feature;
import org.eclipse.equinox.p2.publisher.eclipse.FeatureEntry;
import org.eclipse.packagedrone.repo.api.upload.UploadError;
import org.eclipse.packagedrone.repo.api.upload.UploadResult;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.spotify.docker.client.shaded.javax.ws.rs.client.Client;
import com.spotify.docker.client.shaded.javax.ws.rs.client.ClientBuilder;
import com.spotify.docker.client.shaded.javax.ws.rs.client.Entity;
import com.spotify.docker.client.shaded.javax.ws.rs.client.WebTarget;
import com.spotify.docker.client.shaded.javax.ws.rs.core.Response;
import com.spotify.docker.client.shaded.org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import com.spotify.docker.client.shaded.org.glassfish.jersey.jackson.JacksonFeature;

import eu.gemtec.packagedrone.deploy.impl.UploadException;
import eu.gemtec.packagedrone.deploy.impl.entity.ArtifactType;
import eu.gemtec.packagedrone.deploy.impl.entity.PackageDroneJarArtifact;
import eu.gemtec.packagedrone.deploy.impl.entity.PackageDroneJarArtifact.GAV;
import eu.gemtec.packagedrone.deploy.impl.entity.PackageDroneOsgiArtifact;
import eu.gemtec.packagedrone.deploy.impl.entity.PackageDroneOsgiArtifact.OsgiMetadata;

/**
 * @author Veselin Markov
 *
 */
public class UploadClient {
	private static final String CHANNEL_ID_PARAM = "channelId";
	private static final String PARENT_ID_PARAM = "parentId";
	private static final String FILENAME_PARAM = "artifactName";
	private static final String UPLOAD_TO_CHANNEL = "/api/v3/upload/plain/channel/{" + CHANNEL_ID_PARAM + "}/{" + FILENAME_PARAM + "}";
	private static final String UPLOAD_TO_ARTIFACT = "/api/v3/upload/plain/artifact/{" + CHANNEL_ID_PARAM + "}/{" + PARENT_ID_PARAM + "}/{" + FILENAME_PARAM + "}";

	private final String channel;
	private final boolean uploadPom;
	private final boolean uploadChildArtifacts;
	private final FeatureParser featureParser = new FeatureParser();
	private final WebTarget target;
	private final BuildLogger logger;

	public UploadClient(String key,
						String channel,
						String host,
						boolean uploadPom,
						boolean uploadChildArtifacts,
						BuildLogger logger) {
		this.channel = channel;
		this.uploadPom = uploadPom;
		this.uploadChildArtifacts = uploadChildArtifacts;
		this.logger = logger;
		HttpAuthenticationFeature authentication = HttpAuthenticationFeature.basic("deploy", key);
		Client client = ClientBuilder.newBuilder().register(JacksonFeature.class).register(authentication).build();
		target = client.target("http://" + host);
	}

	public static PackageDroneJarArtifact makeArtifact(File file, GAV gav) throws IOException {
		try (JarFile jar = new JarFile(file)) {
			Feature parsedFeature = new FeatureParser().parse(file);

			ArtifactType artifactType = getArtifactType(jar, parsedFeature);
			OsgiMetadata osgiInfo = getOsgiInfo(file, jar, parsedFeature, artifactType);
			if (osgiInfo != null)
				return new PackageDroneOsgiArtifact(file.getAbsolutePath(), gav, osgiInfo);
			return new PackageDroneJarArtifact(file.getAbsolutePath(), gav, artifactType);
		}
	}

	private static OsgiMetadata getOsgiInfo(File file, JarFile jar, Feature parsedFeature, ArtifactType artifactType) throws IOException {
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

	private static ArtifactType getArtifactType(JarFile file, Feature feature) throws IOException {
		if (feature == null) {
			Attributes attributes = file.getManifest().getMainAttributes();
			if (!attributes.containsKey("Bundle-SymbolicName")) {
				return ArtifactType.MAVEN_MODULE;
			}
			String osgiId = attributes.getValue("Bundle-SymbolicName").split(";")[0];
			if (attributes.containsKey("Fragment-Host")) {
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

	/**
	 * Uploads a single artifact.
	 */
	public void tryUploadArtifact(PackageDroneJarArtifact pdArtifact, List<PackageDroneJarArtifact> bundles, BuildLogger buildLogger) throws IOException, UploadException {
		uploadRootArtifact(pdArtifact, bundles, buildLogger);
	}

	/**
	 * Uploads a feature and all belonging artifacts.
	 */
	public void tryUploadFeature(PackageDroneJarArtifact pdArtifact, List<PackageDroneJarArtifact> bundleList, BuildLogger buildLogger) throws IOException, UploadException {
		buildLogger.addBuildLogEntry("Uploading Feature: " + pdArtifact.getId());
		if (pdArtifact.getType() != ArtifactType.FEATURE) {
			throw new RuntimeException("The artifact isn't a feature");
		}
		uploadRootArtifact(pdArtifact, bundleList, buildLogger);
		Feature feature = featureParser.parse(new File(pdArtifact.getFile()));

		for (PackageDroneJarArtifact pdBundleArtifacts : bundleList) {
			buildLogger.addBuildLogEntry("Checking " + pdBundleArtifacts.getId() + " for feature");
			if (featureHasArtifact(feature, pdBundleArtifacts)) {
				buildLogger.addBuildLogEntry("Feature " + pdArtifact.getId() + " has artifact, uploading it too: " + pdBundleArtifacts.getId());
				uploadChildArtifact(pdBundleArtifacts, bundleList, pdArtifact, buildLogger);
			}
		}
	}

	private void uploadRootArtifact(PackageDroneJarArtifact pdArtifact, List<PackageDroneJarArtifact> bundles, BuildLogger buildLogger) throws IOException, UploadException {
		buildLogger.addBuildLogEntry("Uploading Root Artifact: " + pdArtifact.getId());
		WebTarget uploadTarget = createTarget(target, UPLOAD_TO_CHANNEL, FILENAME_PARAM, getArtifactName(pdArtifact), CHANNEL_ID_PARAM, channel);
		upload(target, uploadTarget, pdArtifact, buildLogger);

		if (uploadChildArtifacts) {
			for (PackageDroneJarArtifact packageDroneJarArtifact : bundles) {
				if (rootBundleHasChild(pdArtifact, packageDroneJarArtifact)) {
					buildLogger.addBuildLogEntry("Root Artifact " + pdArtifact.getId() + " has child, uploading it too: " + packageDroneJarArtifact.getId());
					uploadChildArtifact(packageDroneJarArtifact, bundles, pdArtifact, buildLogger);
				}
			}
		}
	}

	private void uploadChildArtifact(PackageDroneJarArtifact pdArtifact, List<PackageDroneJarArtifact> bundles, PackageDroneJarArtifact pdParentArtifact, BuildLogger buildLogger) throws IOException, UploadException {
		buildLogger.addBuildLogEntry("Uploading Child Artifact: " + pdArtifact.getId() + " for parent: " + pdParentArtifact.getId());
		WebTarget uploadTarget = createTarget(target, UPLOAD_TO_ARTIFACT, FILENAME_PARAM, getArtifactName(pdArtifact), CHANNEL_ID_PARAM, channel, PARENT_ID_PARAM, pdParentArtifact.getPackageDroneId());
		upload(target, uploadTarget, pdArtifact, buildLogger);

		if (uploadChildArtifacts) {
			for (PackageDroneJarArtifact packageDroneJarArtifact : bundles) {
				if (rootBundleHasChild(pdArtifact, packageDroneJarArtifact)) {
					uploadChildArtifact(packageDroneJarArtifact, bundles, pdArtifact, buildLogger);
				}
			}
		}
	}

	private void upload(WebTarget baseTarget, WebTarget uploadTarget, PackageDroneJarArtifact pdArtifact, BuildLogger buildLogger) throws IOException, UploadException {

		buildLogger.addBuildLogEntry("Uploading " + pdArtifact.getFile());

		String srcName = pdArtifact.getFile();
		try (FileInputStream fis = new FileInputStream(pdArtifact.getFile())) {

			uploadTarget = uploadTarget.queryParam("mvn:artifactId", pdArtifact.getGav().getMavenArtifact());
			uploadTarget = uploadTarget.queryParam("mvn:groupId", pdArtifact.getGav().getMavenGroup());
			uploadTarget = uploadTarget.queryParam("mvn:snapshotVersion", pdArtifact.getVersion());
			uploadTarget = uploadTarget.queryParam("mvn:version", pdArtifact.getGav().getMavenVersion());
			if (pdArtifact.getType() == ArtifactType.SOURCE_BUNDLE || pdArtifact.getType() == ArtifactType.SOURCE_FEATURE)
				uploadTarget = uploadTarget.queryParam("mvn:classifier", "sources");
			uploadTarget = uploadTarget.queryParam("mvn:extension", "jar");

			Response putresp = doUpload(uploadTarget, srcName, fis);
			if (putresp.getStatus() == 200) {
				UploadResult uploadResult = new Gson().fromJson(new InputStreamReader((InputStream) putresp.getEntity()), new TypeToken<UploadResult>() {}.getType());
				String packageDroneId = uploadResult.getCreatedArtifacts().get(0).getId();
				pdArtifact.setPackageDroneId(packageDroneId);

				if (uploadPom && pdArtifact.getType() != ArtifactType.SOURCE_BUNDLE && pdArtifact.getType() != ArtifactType.SOURCE_FEATURE) {
					uploadPom(baseTarget, pdArtifact);
				}
			} else {
				UploadError errorResponse = new Gson().fromJson(new InputStreamReader((InputStream) putresp.getEntity()), new TypeToken<UploadError>() {}.getType());
				throw new UploadException("Got RespoonseCode=" + putresp.getStatus() + ", Message=" + errorResponse.getMessage() + "\nExpected ResponseCode=200");
			}
		}
	}

	private void uploadPom(WebTarget baseTarget, PackageDroneJarArtifact pdArtifact) throws IOException, UploadException {
		try (InputStream pomStream = getPom(pdArtifact)) {
			if (pomStream == null)
				return;

			String pomfileName = pdArtifact.getId() + "-" + pdArtifact.getVersion() + ".pom";
			WebTarget uploadTarget = createTarget(baseTarget, UPLOAD_TO_ARTIFACT, FILENAME_PARAM, pomfileName, CHANNEL_ID_PARAM, channel, PARENT_ID_PARAM, pdArtifact.getPackageDroneId());
			uploadTarget = uploadTarget.queryParam("mvn:artifactId", pdArtifact.getGav().getMavenArtifact());
			uploadTarget = uploadTarget.queryParam("mvn:groupId", pdArtifact.getGav().getMavenGroup());
			uploadTarget = uploadTarget.queryParam("mvn:version", pdArtifact.getGav().getMavenVersion());
			uploadTarget = uploadTarget.queryParam("mvn:extension", "pom");

			Response putresp = doUpload(uploadTarget, "pom.xml", pomStream);
			if (putresp.getStatus() != 200) {
				UploadError errorResponse = new Gson().fromJson(new InputStreamReader((InputStream) putresp.getEntity()), new TypeToken<UploadError>() {}.getType());
				throw new UploadException("Got RespoonseCode=" + putresp.getStatus() + ", Message=" + errorResponse.getMessage() + "\nExpected ResponseCode=200");
			}
		}
	}

	private InputStream getPom(PackageDroneJarArtifact pdArtifact) throws IOException {
		String group = pdArtifact.getGav().getMavenGroup();
		String artifact = pdArtifact.getGav().getMavenArtifact();
		try (JarFile jar = new JarFile(pdArtifact.getFile())) {
			ZipEntry pom = jar.getEntry("META-INF" + "/maven/" + group + "/" + artifact + "/pom.xml");
			if (pom == null)
				return null;
			InputStream pomStream = jar.getInputStream(pom);
			return pomStream;
		}
	}

	private Response doUpload(WebTarget uploadTarget, String srcName, InputStream fis) {
		long start = System.currentTimeMillis();
		try {
			logger.addBuildLogEntry("Start uploading " + srcName + " to " + uploadTarget.getUri().toURL().toString());
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		Response putresp = uploadTarget.request(MediaType.APPLICATION_JSON).put(Entity.entity(fis, MediaType.APPLICATION_OCTET_STREAM));
		long end = System.currentTimeMillis();
		putresp.bufferEntity();

		if (putresp.getStatus() == 200) {
			long ms = end - start;
			logger.addBuildLogEntry("Uploaded " + srcName + " to " + uploadTarget.getUri() + " in " + ms + "ms.");
		} else {
			logger.addBuildLogEntry("Upload of " + srcName + " failed.");
			throw new RuntimeException("Upload of " + srcName + " failed");
		}

		return putresp;
	}

	private String getArtifactName(PackageDroneJarArtifact pdArtifact) {
		return pdArtifact.getId() + "-" + pdArtifact.getVersion() + ".jar";
	}

	private boolean featureHasArtifact(Feature feature, PackageDroneJarArtifact pdArtifact) {
		FeatureEntry[] entries = feature.getEntries();
		for (FeatureEntry featureEntry : entries) {
			if (featureEntry.isPlugin()) {
				boolean sameId = featureEntry.getId().equals(pdArtifact.getId());
				boolean sameVersion = featureEntry.getVersion().equals(pdArtifact.getVersion());
				if (sameId && sameVersion)
					return true;
			}
		}
		return false;
	}

	public boolean featureHasArtifact(PackageDroneJarArtifact pdArtifact, PackageDroneJarArtifact pda) {
		return featureHasArtifact(featureParser.parse(new File(pdArtifact.getFile())), pda);
	}

	public boolean rootBundleHasChild(PackageDroneJarArtifact pdArtifact, PackageDroneJarArtifact pda) {
		return pdArtifact.hasChild(pda);
	}

	private WebTarget createTarget(WebTarget baseTarget, String path, String... paramsAndValues) {
		WebTarget webTarget = baseTarget.path(path);
		for (int i = 0; i < paramsAndValues.length; i++) {
			webTarget = webTarget.resolveTemplate(paramsAndValues[i], paramsAndValues[++i]);
		}

		return webTarget;
	}

}
