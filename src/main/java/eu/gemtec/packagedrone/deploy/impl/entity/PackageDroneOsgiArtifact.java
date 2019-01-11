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
package eu.gemtec.packagedrone.deploy.impl.entity;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.eclipse.equinox.internal.p2.publisher.eclipse.FeatureParser;
import org.eclipse.equinox.p2.metadata.Version;
import org.eclipse.equinox.p2.metadata.VersionRange;
import org.eclipse.equinox.p2.publisher.eclipse.Feature;
import org.eclipse.equinox.p2.publisher.eclipse.FeatureEntry;

/**
 * Represents an OSGi-compatible JAR file as artifact.
 * 
 * @author Peter Jeschke
 */
public class PackageDroneOsgiArtifact extends PackageDroneJarArtifact {

	private final OsgiMetadata osgi;

	public PackageDroneOsgiArtifact(String jarFilePath,
									GAV gav,
									OsgiMetadata osgi) {
		super(jarFilePath, gav, osgi.type);
		this.osgi = osgi;
	}

	@Override
	public String getVersion() {
		return osgi.version.toString();
	}

	@Override
	public String getId() {
		return osgi.id;
	}

	public OsgiMetadata getOsgi() {
		return osgi;
	}

	public static class OsgiMetadata {
		private final String id;
		private final Version version;
		private final ArtifactType type;

		public OsgiMetadata(String id,
							Version version,
							ArtifactType type) {
			this.id = id;
			this.version = version;
			this.type = type;
		}

		public String getId() {
			return id;
		}

		public Version getVersion() {
			return version;
		}

		public ArtifactType getType() {
			return type;
		}

		@Override
		public String toString() {
			return "OsgiMetadata [id=" + id + ", version=" + version + ", type=" + type + "]";
		}
	}

	@Override
	public boolean hasChild(PackageDroneJarArtifact pda) throws IOException {
		if (super.hasChild(pda))
			return true;
		if (!(pda instanceof PackageDroneOsgiArtifact)) {
			return false;
		}
		PackageDroneOsgiArtifact osgiChild = (PackageDroneOsgiArtifact) pda;
		switch (osgi.type) {
			case BUNDLE:
				return isFragmentOf(osgiChild);
			case FEATURE:
				return isBundleOf(osgiChild);
			case FRAGMENT:
			case TEST_FRAGMENT:
				// the only case where fragments have children, is when the other artifact is a
				// source bundle, which is checked by super.hasChild
			case SOURCE_BUNDLE:
			case SOURCE_FEATURE:
			case SOURCE_FRAGMENT:
			case SOURCE_TEST_FRAGMENT:
				// source artifacts can't have children
				return false;
			case MAVEN_MODULE:
			case UNDEFINED:
			default:
				throw new RuntimeException("");

		}
	}

	/**
	 * Checks whether the given artifact is a bundle that is included in this feature. If
	 * {@code this} is not a feature, the method will return false.
	 */
	private boolean isBundleOf(PackageDroneOsgiArtifact osgiChild) {
		FeatureParser parser = new FeatureParser();
		Feature feature = parser.parse(new File(getFile()));
		if (feature == null) {
			return false;
		}
		for (FeatureEntry entry : feature.getEntries()) {
			if (entry.getId().equals(osgiChild.getId()) && entry.getVersion().equals(osgiChild.getVersion())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks whether the given artifact is an artifact (or test artifact) of this bundle.
	 */
	private boolean isFragmentOf(PackageDroneOsgiArtifact pda) throws IOException {
		if (pda.osgi.type != ArtifactType.FRAGMENT && pda.osgi.type != ArtifactType.TEST_FRAGMENT) {
			return false;
		}
		try (JarFile jar = new JarFile(pda.getFile())) {
			Manifest manifest = jar.getManifest();
			String fragmentHost = manifest.getMainAttributes().getValue("Fragment-Host");
			String hostId = fragmentHost.split(";")[0];
			VersionRange bundleVersion = getBundleVersion(fragmentHost);
			if (hostId.equals(osgi.id) && bundleVersion.isIncluded(osgi.version)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Retrieved a version range from a {@code Fragment-Host} property.
	 * 
	 * If the Fragment-Host specifies no version, a generic version range of {@code [0.0.0,)} is
	 * returned.
	 * 
	 * @param fragmentHost
	 *            e.g. {@code Fragment-Host: my.bundle;bundle-version="[1.0.0,2.0.0)"}
	 * @return a version range that represents the version in the Fragment-Host property
	 */
	private VersionRange getBundleVersion(String fragmentHost) {
		VersionRange bundleVersion = new VersionRange("0.0.0"); // see osgi.core 7, 3.14.1s
		if (fragmentHost.split(";").length > 1) {
			String[] split = fragmentHost.split(";");
			for (int i = 1; i < split.length; i++) {
				String parameter = split[i];
				if (parameter.startsWith("bundle-version=")) {
					bundleVersion = new VersionRange(parameter.substring(16, parameter.length() - 1));
				}
			}
		}
		return bundleVersion;
	}
}
