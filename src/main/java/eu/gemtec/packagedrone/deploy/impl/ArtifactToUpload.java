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
package eu.gemtec.packagedrone.deploy.impl;

import org.jetbrains.annotations.Nullable;

import com.atlassian.bamboo.plan.artifact.ImmutableArtifactDefinition;
import com.atlassian.bamboo.plan.artifact.ImmutableArtifactSubscription;
import com.atlassian.bamboo.task.TaskDefinition;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;

public final class ArtifactToUpload {

	public static final int ARTIFACT_ID_ALL_ARTIFACTS = -1;
	public static final int ARTIFACT_ID_LOCAL_FILES = -2;

	private static final Integer NO_TRANSFER_ID = -1;

	private final Long artifactIdOrType;
	private final Long artifactDownloaderTaskId;
	private final Integer artifactDownloaderTransferId;
	private final String name;

	public ArtifactToUpload(String configEntry) {
		final String[] data = configEntry.split(":", 4);

		artifactIdOrType = Long.valueOf(data[0]);

		Integer transferId = Integer.valueOf(data[2]);
		if (transferId.equals(NO_TRANSFER_ID)) {
			artifactDownloaderTaskId = null;
			artifactDownloaderTransferId = null;
		} else {
			artifactDownloaderTaskId = Long.valueOf(data[1]);
			artifactDownloaderTransferId = transferId;
		}

		name = data[3];
	}

	public Long getArtifactIdOrType() {
		return artifactIdOrType;
	}

	public String getName() {
		return name;
	}

	public Integer getArtifactDownloaderTransferId() {
		return artifactDownloaderTransferId;
	}

	public Long getArtifactDownloaderTaskId() {
		return artifactDownloaderTaskId;
	}

	private ArtifactToUpload(	long artifactIdOrType,
								@Nullable Long artifactDownloaderTaskId,
								@Nullable Integer artifactDownloaderTransferId,
								String name) {
		this.artifactIdOrType = artifactIdOrType;
		this.artifactDownloaderTaskId = artifactDownloaderTaskId;
		this.artifactDownloaderTransferId = artifactDownloaderTransferId;
		this.name = name;
	}

	public static ArtifactToUpload from(ImmutableArtifactSubscription artifactSubscription) {
		return new ArtifactToUpload(artifactSubscription.getArtifactDefinition().getId(), null, null, artifactSubscription.getName());
	}

	public static ArtifactToUpload from(ImmutableArtifactDefinition artifactDefinition) {
		return new ArtifactToUpload(artifactDefinition.getId(), null, null, artifactDefinition.getName());
	}

	public static ArtifactToUpload fromTransferTask(long artifactId, TaskDefinition task, int transferId, String artifactName) {
		return new ArtifactToUpload(artifactId, task.getId(), transferId, artifactName);
	}

	@Override
	public String toString() {
		return Joiner.on(':').join(artifactIdOrType, MoreObjects.firstNonNull(artifactDownloaderTaskId, NO_TRANSFER_ID), MoreObjects.firstNonNull(artifactDownloaderTransferId, NO_TRANSFER_ID), name);
	}

	public boolean isAllArtifacts() {
		return artifactIdOrType == ARTIFACT_ID_ALL_ARTIFACTS;
	}

	public boolean isLocalFiles() {
		return artifactIdOrType == ARTIFACT_ID_LOCAL_FILES;
	}

	public boolean isFromTransferTask() {
		return artifactDownloaderTaskId != null;
	}
}