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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a JAR file as package drone artifact.
 * 
 * @author Peter Jeschke
 */
public class PackageDroneJarArtifact {

	private final String jarFilePath;
	private final GAV gav;
	private final ArtifactType type;
	private String packageDroneId;
	private final List<PackageDroneJarArtifact> children = new ArrayList<>();

	public PackageDroneJarArtifact(	String jarFilePath,
									GAV gav,
									ArtifactType type) {
		this.jarFilePath = jarFilePath;
		this.gav = gav;
		this.type = type;
	}

	public String getVersion() {
		return gav.mavenVersion;
	}

	public String getId() {
		return gav.mavenArtifact;
	}

	public ArtifactType getType() {
		return type;
	}

	public String getFile() {
		return jarFilePath;
	}

	public GAV getGav() {
		return gav;
	}

	public String getPackageDroneId() {
		return packageDroneId;
	}

	public void setPackageDroneId(String packageDroneId) {
		this.packageDroneId = packageDroneId;
	}

	public List<PackageDroneJarArtifact> getChildren() {
		return children;
	}

	@Override
	public String toString() {
		return "JarPackageDroneArtifact [gav=" + gav + ", packageDroneId=" + packageDroneId + "]";
	}

	public static class GAV {
		private final String mavenGroup;
		private final String mavenArtifact;
		private final String mavenVersion;

		public GAV(	String mavenGroup,
					String mavenArtifact,
					String mavenVersion) {
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

	/**
	 * Checks whether pda is a child artifact of {@code this}.
	 * 
	 * @throws IOException
	 *             might be thrown my implementing classes
	 */
	public boolean hasChild(PackageDroneJarArtifact pda) throws IOException {
		String rootNameWithoutExtension = jarFilePath.substring(0, jarFilePath.length() - 4);

		String childFilename = pda.getFile();
		String childNameWithoutExtension = childFilename.substring(0, childFilename.length() - 4);
		// z.B. bundle.jar und bundle-sources.jar
		return childNameWithoutExtension.startsWith(rootNameWithoutExtension + "-");
	}

	/**
	 * Filters all children of this artifact from the given list and returns them.
	 * 
	 * @param artifacts
	 *            a list of artifacts that should be inspected
	 * @return a list of children of this artifact. Is a subset of {@code artifacts}
	 * @throws IOException
	 *             if some files couldn't be read to get information
	 */
	public List<PackageDroneJarArtifact> findChildren(List<PackageDroneJarArtifact> artifacts) throws IOException {
		List<PackageDroneJarArtifact> children = new ArrayList<>();
		for (PackageDroneJarArtifact artifact : artifacts) {
			if (hasChild(artifact)) {
				children.add(artifact);
			}
		}
		return children;
	}
}