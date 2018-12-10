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

import org.apache.maven.artifact.Artifact;

/**
 * @author Florian Reinecke
 *
 */
public class ArtifactFile {
	private Artifact artifact;
	private ArtifactType type;
	private File source;
	private String id;

	/**
	 * 
	 */
	public ArtifactFile() {
		super();
	}

	/**
	 * @param id
	 * @param version
	 * @param type
	 */
	public ArtifactFile(Artifact artifact,
						ArtifactType type) {
		super();
		this.artifact = artifact;
		this.type = type;
	}

	/**
	 * @return the artifact
	 */
	public Artifact getArtifact() {
		return artifact;
	}

	/**
	 * @param artifact
	 *            the artifact to set
	 */
	public void setArtifact(Artifact artifact) {
		this.artifact = artifact;
	}

	/**
	 * @return the type
	 */
	public ArtifactType getType() {
		return type;
	}

	/**
	 * @param type
	 *            the type to set
	 */
	public void setType(ArtifactType type) {
		this.type = type;
	}

	/**
	 * @return the source
	 */
	public File getSource() {
		return source;
	}

	/**
	 * @param source
	 *            the source to set
	 */
	public void setSource(File source) {
		this.source = source;
	}

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @param id
	 *            the id to set
	 */
	public void setId(String id) {
		this.id = id;
	}

}
