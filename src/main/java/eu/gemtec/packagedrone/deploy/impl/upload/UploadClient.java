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
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import javax.ws.rs.core.MediaType;

import org.eclipse.equinox.internal.p2.publisher.eclipse.FeatureParser;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.publisher.eclipse.Feature;
import org.eclipse.equinox.p2.publisher.eclipse.FeatureEntry;
import org.eclipse.packagedrone.repo.api.upload.UploadError;
import org.eclipse.packagedrone.repo.api.upload.UploadResult;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.spotify.docker.client.shaded.javax.ws.rs.client.Client;
import com.spotify.docker.client.shaded.javax.ws.rs.client.ClientBuilder;
import com.spotify.docker.client.shaded.javax.ws.rs.client.Entity;
import com.spotify.docker.client.shaded.javax.ws.rs.client.WebTarget;
import com.spotify.docker.client.shaded.javax.ws.rs.core.Response;

import com.spotify.docker.client.shaded.org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import com.spotify.docker.client.shaded.org.glassfish.jersey.jackson.JacksonFeature;

import eu.gemtec.packagedrone.deploy.impl.entity.PackageDroneArtifact;
import eu.gemtec.packagedrone.deploy.impl.entity.Type;
import eu.gemtec.packagedrone.deploy.impl.entity.PackageDroneArtifact.GAV;
import eu.gemtec.packagedrone.deploy.impl.entity.PackageDroneArtifact.OsgiMetadata;

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

	private final String key;

	private final String channel;

	private final String host;

	private final boolean uploadPom;

	private FeatureParser featureParser = new FeatureParser();

	private Logger logger;

	public UploadClient(String key, String channel, String host, boolean uploadPom) {
		this.key = key;
		this.channel = channel;
		this.host = host;
		this.uploadPom = uploadPom;
	}

	public static PackageDroneArtifact makeAtrifact(File file, GAV gav) throws IOException {
		JarFile jar = new JarFile(file);
		PackageDroneArtifact pdSourceArtifact = null;

		Type artifactType;
		Version version;
		String osgiId;
		FeatureParser featureParser = new FeatureParser();
		Feature parsedFeature = featureParser.parse(file);
		if (parsedFeature != null) {
			version = Version.parseVersion(parsedFeature.getVersion());
			osgiId = parsedFeature.getId();
			if (osgiId.endsWith(".source"))
				artifactType = Type.SourceFeature;
			else
				artifactType = Type.Feature;

			String sourceFeatureFileName = jar.getName().replace(".jar", "-sources-feature.jar");
			File sourceFile = new File(sourceFeatureFileName);
			if (sourceFile.exists()) {
				pdSourceArtifact = makeAtrifact(sourceFile, gav);
			}
		} else {
			Manifest manifest = jar.getManifest();
			version = Version.parseVersion(manifest.getMainAttributes().getValue("Bundle-Version"));
			osgiId = manifest.getMainAttributes().getValue("Bundle-SymbolicName").split(";")[0];
			String hostBundle = manifest.getMainAttributes().getValue("Fragment-Host");

			if (osgiId.endsWith(".source")) {
				if (hostBundle != null)
					if ((osgiId.endsWith(".test.source") || osgiId.endsWith(".tests.source")))
						artifactType = Type.SourceTestFragment;
					else
						artifactType = Type.SourceFragment;
				else
					artifactType = Type.SourceBundle;

			} else {
				if (hostBundle != null)
					if ((osgiId.endsWith(".test") || osgiId.endsWith(".tests")))
						artifactType = Type.TestFragment;
					else
						artifactType = Type.Fragment;
				else
					artifactType = Type.Bundle;
			}

			String sourceFileName = jar.getName().replace(".jar", "-sources.jar");
			File sourceFile = new File(sourceFileName);
			if (sourceFile.exists()) {
				pdSourceArtifact = makeAtrifact(sourceFile, gav);
			}
		}
		jar.close();
		OsgiMetadata osgi = new OsgiMetadata(osgiId, version, artifactType);
		PackageDroneArtifact pdArtifact = new PackageDroneArtifact(jar, gav, osgi, pdSourceArtifact);
		return pdArtifact;
	}

	public void tryUpload(PackageDroneArtifact pdArtifact, List<PackageDroneArtifact> featureList, List<PackageDroneArtifact> bundleList) throws Exception {

		HttpAuthenticationFeature authentication = HttpAuthenticationFeature.basic("deploy", key);
		Client client = ClientBuilder.newBuilder().register(JacksonFeature.class).register(authentication).build();
		WebTarget target = client.target("http://" + host);

		if (pdArtifact.getOsgi().getType() == Type.Feature) {
			uploadRootArtifact(target, pdArtifact);
			Feature feature = featureParser.parse(new File(pdArtifact.getFile().getName()));

			for (Iterator<PackageDroneArtifact> iterator = bundleList.iterator(); iterator.hasNext();) {
				PackageDroneArtifact pdBundleArtifacts = iterator.next();
				if (featureHasArtifact(feature, pdBundleArtifacts)) {
					uploadChildArtifact(target, pdBundleArtifacts, pdArtifact);
					iterator.remove();
				}
			}

		} else if (pdArtifact.getOsgi().getType() == Type.Bundle || pdArtifact.getOsgi().getType() == Type.Fragment) {
			PackageDroneArtifact pdParentArtifact = getParentFeature(pdArtifact, featureList);
			if (pdParentArtifact == null)
				return;

			if ((pdParentArtifact.getPackageDroneId() == null))
				tryUpload(pdParentArtifact, featureList, bundleList);

			uploadChildArtifact(target, pdArtifact, pdParentArtifact);
		}
	}

	private void uploadRootArtifact(WebTarget target, PackageDroneArtifact pdArtifact) throws Exception {
		WebTarget uploadTarget = createTarget(target, UPLOAD_TO_CHANNEL, FILENAME_PARAM, getArtifactName(pdArtifact), CHANNEL_ID_PARAM, channel);
		upload(target, uploadTarget, pdArtifact);
	}

	private void uploadChildArtifact(WebTarget target, PackageDroneArtifact pdArtifact, PackageDroneArtifact pdParentArtifact) throws Exception {
		WebTarget uploadTarget = createTarget(target, UPLOAD_TO_ARTIFACT, FILENAME_PARAM, getArtifactName(pdArtifact), CHANNEL_ID_PARAM, channel, PARENT_ID_PARAM,
				pdParentArtifact.getPackageDroneId());
		upload(target, uploadTarget, pdArtifact);
	}

	private void upload(WebTarget baseTarget, WebTarget uploadTarget, PackageDroneArtifact pdArtifact) throws Exception {

		try {
			String srcName = pdArtifact.getFile().getName();
			FileInputStream fis = new FileInputStream(pdArtifact.getFile().getName());

			uploadTarget = uploadTarget.queryParam("mvn:artifactId", pdArtifact.getGav().getMavenArtifact());
			uploadTarget = uploadTarget.queryParam("mvn:groupId", pdArtifact.getGav().getMavenGroup());
			uploadTarget = uploadTarget.queryParam("mvn:snapshotVersion", pdArtifact.getOsgi().getVersion().toString());
			uploadTarget = uploadTarget.queryParam("mvn:version", pdArtifact.getGav().getMavenVersion());
			if (pdArtifact.getOsgi().getType() == Type.SourceBundle || pdArtifact.getOsgi().getType() == Type.SourceFeature)
				uploadTarget = uploadTarget.queryParam("mvn:classifier", "sources");
			// else
			// uploadTarget = uploadTarget.queryParam("mvn:classifier", "");
			uploadTarget = uploadTarget.queryParam("mvn:extension", "jar");

			Response putresp = doUpload(uploadTarget, srcName, fis);
			if (putresp.getStatus() == 200) {
				InputStream response = (InputStream) putresp.getEntity();
				UploadResult uploadResult = new Gson().fromJson(new InputStreamReader(response), new TypeToken<UploadResult>() { }.getType());
				String packageDroneId = uploadResult.getCreatedArtifacts().get(0).getId();
				pdArtifact.setPackageDroneId(packageDroneId);

				if (uploadPom && pdArtifact.getOsgi().getType() != Type.SourceBundle && pdArtifact.getOsgi().getType() != Type.SourceFeature)
					uploadPom(baseTarget, pdArtifact);

				PackageDroneArtifact sourceArtifact = pdArtifact.getSource();
				if (sourceArtifact != null) {
					uploadChildArtifact(baseTarget, sourceArtifact, pdArtifact);
				}
			} else {
				UploadError errorResponse =  new Gson().fromJson(new InputStreamReader( (InputStream) putresp.getEntity()), new TypeToken<UploadError>() { }.getType());
				throw new Exception("Got RespoonseCode=" + putresp.getStatus() + ", Message=" + errorResponse.getMessage() + "\nExpected ResponseCode=200");
			}
		} catch (IOException e) {
			throw new Exception("Error while reading jar file", e);
		}
	}

	private void uploadPom(WebTarget baseTarget, PackageDroneArtifact pdArtifact) throws IOException, Exception {
		InputStream pomStream = getPom(pdArtifact);
		if (pomStream == null)
			return;

		String pomfileName = pdArtifact.getOsgi().getId() + "-" + pdArtifact.getOsgi().getVersion() + ".pom";
		WebTarget uploadTarget = createTarget(baseTarget, UPLOAD_TO_ARTIFACT, FILENAME_PARAM, pomfileName, CHANNEL_ID_PARAM, channel, PARENT_ID_PARAM,
				pdArtifact.getPackageDroneId());
		uploadTarget = uploadTarget.queryParam("mvn:artifactId", pdArtifact.getGav().getMavenArtifact());
		uploadTarget = uploadTarget.queryParam("mvn:groupId", pdArtifact.getGav().getMavenGroup());
		uploadTarget = uploadTarget.queryParam("mvn:version", pdArtifact.getGav().getMavenVersion());
		uploadTarget = uploadTarget.queryParam("mvn:extension", "pom");

		Response putresp = doUpload(uploadTarget, "pom.xml", pomStream);
		if (putresp.getStatus() != 200) {
			UploadError errorResponse =  new Gson().fromJson(new InputStreamReader( (InputStream) putresp.getEntity()), new TypeToken<UploadError>() { }.getType());
			throw new Exception("Got RespoonseCode=" + putresp.getStatus() + ", Message=" + errorResponse.getMessage() + "\nExpected ResponseCode=200");
		}
	}

	private InputStream getPom(PackageDroneArtifact pdArtifact) throws IOException {
		String group = pdArtifact.getGav().getMavenGroup();
		String artifact = pdArtifact.getGav().getMavenArtifact();
		ZipEntry pom = pdArtifact.getFile().getEntry("META-INF" + "/maven/" + group + "/" + artifact + "/pom.xml");
		if (pom == null)
			return null;
		InputStream pomStream = pdArtifact.getFile().getInputStream(pom);
		return pomStream;
	}

	private Response doUpload(WebTarget uploadTarget, String srcName, InputStream fis) {
		long start = System.currentTimeMillis();
		try {
			getLogger().info("Start uploading " + srcName + " to " + uploadTarget.getUri().toURL().toString());
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		Response putresp = uploadTarget.request(MediaType.APPLICATION_JSON).put(Entity.entity(fis, MediaType.APPLICATION_OCTET_STREAM));
		long end = System.currentTimeMillis();
		putresp.bufferEntity();

		if (putresp.getStatus() == 200) {
			long ms = end - start;
			getLogger().info("Uploaded " + srcName + " to " + uploadTarget.getUri() + " in " + ms + "ms.");
		} else {
			getLogger().error("Uploaded of " + srcName + " failed.");
		}

		return putresp;
	}

	private Logger getLogger() {
		if (logger == null)
			return new Logger() {
				@Override
				public void info(String string) {
				}

				@Override
				public void error(String string) {
				}
			};
		else
			return logger;
	}

	public void setLogger(Logger logger) {
		this.logger = logger;
	}

	private String getArtifactName(PackageDroneArtifact pdArtifact) {
		return pdArtifact.getOsgi().getId() + "-" + pdArtifact.getOsgi().getVersion() + ".jar";
	}

	private PackageDroneArtifact getParentFeature(PackageDroneArtifact pdArtifact, List<PackageDroneArtifact> featureList) {

		for (PackageDroneArtifact featureArtifact : featureList) {
			Feature feature = featureParser.parse(new File(featureArtifact.getFile().getName()));
			if (featureHasArtifact(feature, pdArtifact))
				return featureArtifact;
		}
		return null;
	}

	private boolean featureHasArtifact(Feature feature, PackageDroneArtifact pdArtifact) {
		FeatureEntry[] entries = feature.getEntries();
		for (FeatureEntry featureEntry : entries) {
			if (featureEntry.isPlugin()) {
				boolean sameId = featureEntry.getId().equals(pdArtifact.getOsgi().getId());
				boolean sameVersion = featureEntry.getVersion().equals(pdArtifact.getOsgi().getVersion().toString());
				if (sameId && sameVersion)
					return true;
			}
		}
		return false;
	}

	private WebTarget createTarget(WebTarget baseTarget, String path, String... paramsAndValues) {
		WebTarget webTarget = baseTarget.path(path);
		for (int i = 0; i < paramsAndValues.length; i++) {
			webTarget = webTarget.resolveTemplate(paramsAndValues[i], paramsAndValues[++i]);
		}

		return webTarget;
	}

}
