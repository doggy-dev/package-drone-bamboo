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

import java.util.jar.JarFile;

import org.eclipse.equinox.p2.metadata.Version;

/**
 * @author Veselin Markov
 *
 */
public class PackageDroneArtifact {

	private final JarFile file;
	private final GAV gav;
	private final OsgiMetadata osgi;
	private final PackageDroneArtifact source;

	private String packageDroneId;

	public PackageDroneArtifact(JarFile jar, GAV gav, OsgiMetadata osgi, PackageDroneArtifact source) {
		file = jar;
		this.gav = gav;
		this.osgi = osgi;
		this.source = source;
	}

	public JarFile getFile() {
		return file;
	}

	public GAV getGav() {
		return gav;
	}

	public OsgiMetadata getOsgi() {
		return osgi;
	}

	public PackageDroneArtifact getSource() {
		return source;
	}

	public String getPackageDroneId() {
		return packageDroneId;
	}

	public void setPackageDroneId(String packageDroneId) {
		this.packageDroneId = packageDroneId;
	}

	@Override
	public String toString() {
		return "PackageDroneArtifact [gav=" + gav + ", osgi=" + osgi + ", source=" + source + ", packageDroneId=" + packageDroneId + "]";
	}

	public static class GAV {
		private final String mavenGroup;
		private final String mavenArtifact;
		private final String mavenVersion;

		public GAV(String mavenGroup, String mavenArtifact, String mavenVersion) {
			this.mavenGroup = mavenGroup;
			this.mavenArtifact = mavenArtifact;
			this.mavenVersion = mavenVersion;
		}

		public String getMavenGroup() {
			return mavenGroup;
		}

		public String getMavenArtifact() {
			return mavenArtifact;
		}

		public String getMavenVersion() {
			return mavenVersion;
		}

		@Override
		public String toString() {
			return "GAV [mavenGroup=" + mavenGroup + ", mavenArtifact=" + mavenArtifact + ", mavenVersion=" + mavenVersion + "]";
		}
	}

	public static class OsgiMetadata {
		private final String id;
		private final Version version;
		private final Type type;

		public OsgiMetadata(String id, Version version, Type type) {
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

		public Type getType() {
			return type;
		}

		@Override
		public String toString() {
			return "OsgiMetadata [id=" + id + ", version=" + version + ", type=" + type + "]";
		}

	}
}
