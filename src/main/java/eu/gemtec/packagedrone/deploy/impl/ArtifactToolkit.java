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

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.atlassian.bamboo.plan.artifact.ArtifactDefinitionContext;
import com.atlassian.bamboo.plan.artifact.ArtifactSubscriptionContext;
import com.atlassian.bamboo.plugin.ArtifactDownloaderTaskConfigurationHelper;
import com.atlassian.bamboo.task.CommonTaskContext;
import com.atlassian.bamboo.task.runtime.RuntimeTaskDefinition;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

/**
 * @author Veselin Markov
 *
 */
public class ArtifactToolkit {

	@NotNull
	static ArtifactToUpload getArtifactToScpFromConfig(CommonTaskContext taskContext) {
		final String artifactToUpload = taskContext.getConfigurationMap().get(UploadTaskConfigurator.ARTIFACT_TO_UPLOAD);
		if (artifactToUpload != null) {
			return new ArtifactToUpload(artifactToUpload);
		}
		throw new IllegalArgumentException("unable to find artifact to SCP");
	}

	@Nullable
	static CopyPathSpecs getPathSpecToCopy(CommonTaskContext taskContext, ArtifactToUpload artifactToUpload) {

		if (artifactToUpload.isFromTransferTask()) {
			return artifactToUploadFromTransferTask(taskContext, artifactToUpload);
		}
		return null;
	}

	@Nullable
	private static CopyPathSpecs artifactToUploadFromTransferTask(@NotNull CommonTaskContext taskContext, @NotNull ArtifactToUpload artifactToUpload) {
		final long artifactDownloaderTaskId = Preconditions.checkNotNull(artifactToUpload.getArtifactDownloaderTaskId());
		final Optional<RuntimeTaskDefinition> artifactDownloaderTaskDefinition = taskContext.getCommonContext().getRuntimeTaskDefinitions().stream().filter(taskDefinition -> taskDefinition.getId() == artifactDownloaderTaskId).findAny();
		if (!artifactDownloaderTaskDefinition.isPresent()) {
			return null;
		}

		final Map<String, String> artDownloaderRuntimeTaskContext = Preconditions.checkNotNull(artifactDownloaderTaskDefinition.get().getRuntimeContext(), "Artifact Provider Task has no runtime configuration");

		final Integer artifactDownloaderTransferId = artifactToUpload.getArtifactDownloaderTransferId();
		final Collection<Integer> runtimeArtifactIds = ArtifactDownloaderTaskConfigurationHelper.getRuntimeArtifactIds(artDownloaderRuntimeTaskContext, artifactDownloaderTransferId);

		String localPath = null;
		StringBuilder pathSpecs = new StringBuilder();
		for (int runtimeArtifactId : runtimeArtifactIds) {
			if (pathSpecs.length() > 0) {
				pathSpecs.append(',');
			}
			final String copyPattern = ArtifactDownloaderTaskConfigurationHelper.getCopyPattern(artDownloaderRuntimeTaskContext, runtimeArtifactId);
			pathSpecs.append(copyPattern);
			localPath = ArtifactDownloaderTaskConfigurationHelper.getLocalPath(artDownloaderRuntimeTaskContext, runtimeArtifactId);
		}

		return new CopyPathSpecs(new File(taskContext.getWorkingDirectory(), localPath), pathSpecs.toString(), true);
	}

	public static final class CopyPathSpecs {
		final String copyPattern;
		final boolean isAntPattern;
		final File rootDirectory;

		public CopyPathSpecs(	File rootDirectory,
								String pathSpecs,
								boolean useAntPath) {
			this.rootDirectory = rootDirectory;
			copyPattern = pathSpecs;
			isAntPattern = useAntPath;
		}

		public CopyPathSpecs(	final File rootDirectory,
								ArtifactDefinitionContext artifactDefinitionContext) {
			this.rootDirectory = new File(rootDirectory, artifactDefinitionContext.getLocation());
			copyPattern = artifactDefinitionContext.getCopyPattern();
			isAntPattern = true;
		}

		public CopyPathSpecs(	final File rootDirectory,
								final ArtifactSubscriptionContext artifactSubscriptionContext) {
			this.rootDirectory = new File(rootDirectory, artifactSubscriptionContext.getDestinationPath());
			copyPattern = artifactSubscriptionContext.getArtifactDefinitionContext().getCopyPattern();
			isAntPattern = true;
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this).add("rootDirectory", rootDirectory).add("copyPattern", copyPattern).add("isAntPattern", isAntPattern).toString();
		}
	}
}
