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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.eclipse.packagedrone.repo.api.upload.UploadError;
import org.eclipse.packagedrone.repo.api.upload.UploadResult;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.spotify.docker.client.shaded.javax.ws.rs.client.Client;
import com.spotify.docker.client.shaded.javax.ws.rs.client.ClientBuilder;
import com.spotify.docker.client.shaded.javax.ws.rs.client.Entity;
import com.spotify.docker.client.shaded.javax.ws.rs.client.WebTarget;
import com.spotify.docker.client.shaded.javax.ws.rs.core.MediaType;
import com.spotify.docker.client.shaded.javax.ws.rs.core.Response;
import com.spotify.docker.client.shaded.org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import com.spotify.docker.client.shaded.org.glassfish.jersey.jackson.JacksonFeature;

import eu.gemtec.packagedrone.deploy.impl.UploadException;
import eu.gemtec.packagedrone.deploy.impl.entity.ArtifactType;
import eu.gemtec.packagedrone.deploy.impl.entity.PackageDroneJarArtifact;

/**
 * Uploads Artifacts to PackageDrone.
 * 
 * @author Veselin Markov
 * @author Peter Jeschke
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

	/**
	 * Uploads a single artifact.
	 */
	public void tryUploadArtifact(PackageDroneJarArtifact pdArtifact, BuildLogger buildLogger) throws IOException, UploadException {
		uploadRootArtifact(pdArtifact, buildLogger);
	}

	private void uploadRootArtifact(PackageDroneJarArtifact pdArtifact, BuildLogger buildLogger) throws IOException, UploadException {
		buildLogger.addBuildLogEntry("Uploading Root Artifact: " + pdArtifact.getId());
		WebTarget uploadTarget = createTarget(target, UPLOAD_TO_CHANNEL, FILENAME_PARAM, getArtifactName(pdArtifact), CHANNEL_ID_PARAM, channel);
		upload(uploadTarget, pdArtifact, buildLogger);

		if (uploadChildArtifacts) {
			for (PackageDroneJarArtifact childArtifacts : pdArtifact.getChildren()) {
				buildLogger.addBuildLogEntry("Root Artifact " + pdArtifact.getId() + " has child, uploading it too: " + childArtifacts.getId());
				uploadChildArtifact(childArtifacts, pdArtifact, buildLogger);
			}
		}
	}

	private void uploadChildArtifact(PackageDroneJarArtifact pdArtifact, PackageDroneJarArtifact pdParentArtifact, BuildLogger buildLogger) throws IOException, UploadException {
		buildLogger.addBuildLogEntry("Uploading Child Artifact: " + pdArtifact.getId() + " for parent: " + pdParentArtifact.getId());
		WebTarget uploadTarget = createTarget(target, UPLOAD_TO_ARTIFACT, FILENAME_PARAM, getArtifactName(pdArtifact), CHANNEL_ID_PARAM, channel, PARENT_ID_PARAM, pdParentArtifact.getPackageDroneId());
		upload(uploadTarget, pdArtifact, buildLogger);

		if (uploadChildArtifacts) {
			for (PackageDroneJarArtifact packageDroneJarArtifact : pdArtifact.getChildren()) {
				uploadChildArtifact(packageDroneJarArtifact, pdArtifact, buildLogger);
			}
		}
	}

	private void upload(WebTarget uploadTarget, PackageDroneJarArtifact pdArtifact, BuildLogger buildLogger) throws IOException, UploadException {
		buildLogger.addBuildLogEntry("Uploading " + pdArtifact.getFile());

		String srcName = pdArtifact.getFile();
		try (FileInputStream fis = new FileInputStream(pdArtifact.getFile())) {
			uploadTarget = uploadTarget.queryParam("mvn:artifactId", pdArtifact.getGav().getMavenArtifact());
			uploadTarget = uploadTarget.queryParam("mvn:groupId", pdArtifact.getGav().getMavenGroup());
			uploadTarget = uploadTarget.queryParam("mvn:snapshotVersion", pdArtifact.getVersion());
			uploadTarget = uploadTarget.queryParam("mvn:version", pdArtifact.getGav().getMavenVersion());
			if (pdArtifact.getType() == ArtifactType.SOURCE_BUNDLE || pdArtifact.getType() == ArtifactType.SOURCE_FEATURE) {
				uploadTarget = uploadTarget.queryParam("mvn:classifier", "sources");
			}
			uploadTarget = uploadTarget.queryParam("mvn:extension", "jar");

			Response putresp = doUpload(uploadTarget, srcName, fis);
			if (putresp.getStatus() == 200) {
				UploadResult uploadResult = new Gson().fromJson(new InputStreamReader((InputStream) putresp.getEntity()), new TypeToken<UploadResult>() {}.getType());
				String packageDroneId = uploadResult.getCreatedArtifacts().get(0).getId();
				pdArtifact.setPackageDroneId(packageDroneId);

				if (uploadPom && pdArtifact.getType() != ArtifactType.SOURCE_BUNDLE && pdArtifact.getType() != ArtifactType.SOURCE_FEATURE) {
					uploadPom(pdArtifact);
				}
			} else {
				UploadError errorResponse = new Gson().fromJson(new InputStreamReader((InputStream) putresp.getEntity()), new TypeToken<UploadError>() {}.getType());
				throw new UploadException("Got RespoonseCode=" + putresp.getStatus() + ", Message=" + errorResponse.getMessage() + "\nExpected ResponseCode=200");
			}
		}
	}

	private void uploadPom(PackageDroneJarArtifact pdArtifact) throws IOException, UploadException {
		String group = pdArtifact.getGav().getMavenGroup();
		String artifact = pdArtifact.getGav().getMavenArtifact();
		try (JarFile jar = new JarFile(pdArtifact.getFile())) {
			ZipEntry pom = jar.getEntry("META-INF" + "/maven/" + group + "/" + artifact + "/pom.xml");
			if (pom == null)
				return;
			try (InputStream pomStream = jar.getInputStream(pom)) {

				String pomfileName = pdArtifact.getId() + "-" + pdArtifact.getVersion() + ".pom";
				WebTarget uploadTarget = createTarget(target, UPLOAD_TO_ARTIFACT, FILENAME_PARAM, pomfileName, CHANNEL_ID_PARAM, channel, PARENT_ID_PARAM, pdArtifact.getPackageDroneId());
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
	}

	private com.spotify.docker.client.shaded.javax.ws.rs.core.Response doUpload(WebTarget uploadTarget, String srcName, InputStream fis) {
		long start = System.currentTimeMillis();
		logger.addBuildLogEntry("Start uploading " + srcName + " to " + uploadTarget.getUri().toString());
		com.spotify.docker.client.shaded.javax.ws.rs.core.Response putresp = uploadTarget.request(MediaType.APPLICATION_JSON).put(Entity.entity(fis, MediaType.APPLICATION_OCTET_STREAM));
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

	private WebTarget createTarget(WebTarget baseTarget, String path, String... paramsAndValues) {
		WebTarget webTarget = baseTarget.path(path);
		for (int i = 0; i < paramsAndValues.length; i++) {
			webTarget = webTarget.resolveTemplate(paramsAndValues[i], paramsAndValues[++i]);
		}

		return webTarget;
	}

}
