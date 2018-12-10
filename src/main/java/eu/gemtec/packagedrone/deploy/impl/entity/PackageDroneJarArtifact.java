/*
 * Copyright Gemtec GmbH 2009-2018
 *
 * Erstellt am: 07.12.2018 16:05:59
 * Erstellt von: Christian Schwarz 
 */
package eu.gemtec.packagedrone.deploy.impl.entity;

import java.util.jar.JarFile;

/**
 * @author Christian Schwarz
 *
 */
public class PackageDroneJarArtifact {

	private final JarFile file;
	private final GAV gav;
	private final PackageDroneJarArtifact source;
	private final ArtifactType type;
	private String packageDroneId;

	public PackageDroneJarArtifact(	JarFile file,
									GAV gav,
									ArtifactType type,
									PackageDroneJarArtifact source) {
		this.file = file;
		this.gav = gav;
		this.type = type;
		this.source = source;
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

	public JarFile getFile() {
		return file;
	}

	public GAV getGav() {
		return gav;
	}

	public PackageDroneJarArtifact getSource() {
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
		return "JarPackageDroneArtifact [gav=" + gav + ", source=" + source + ", packageDroneId=" + packageDroneId + "]";
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