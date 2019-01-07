/*
 * Copyright Gemtec GmbH 2009-2018
 *
 * Erstellt am: 07.12.2018 16:05:59
 * Erstellt von: Christian Schwarz 
 */
package eu.gemtec.packagedrone.deploy.impl.entity;

/**
 * @author Christian Schwarz
 *
 */
public class PackageDroneJarArtifact {

	private final String jarFilePath;
	private final GAV gav;
	private final ArtifactType type;
	private String packageDroneId;

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
}