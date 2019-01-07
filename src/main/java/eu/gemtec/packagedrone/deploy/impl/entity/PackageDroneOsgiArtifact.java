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

import org.eclipse.equinox.p2.metadata.Version;

/**
 * @author Veselin Markov
 *
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
}
