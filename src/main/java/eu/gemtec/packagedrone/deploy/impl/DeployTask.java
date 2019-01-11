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
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;

import org.jetbrains.annotations.NotNull;
import org.xml.sax.SAXException;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.configuration.ConfigurationMap;
import com.atlassian.bamboo.deployments.execution.DeploymentTaskContext;
import com.atlassian.bamboo.deployments.execution.DeploymentTaskType;
import com.atlassian.bamboo.task.CommonTaskContext;
import com.atlassian.bamboo.task.TaskException;
import com.atlassian.bamboo.task.TaskResult;
import com.atlassian.bamboo.task.TaskResultBuilder;
import com.atlassian.bamboo.task.TaskState;
import com.atlassian.bamboo.util.BambooIterables;
import com.atlassian.bamboo.utils.FileVisitor;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;

import eu.gemtec.packagedrone.deploy.impl.ArtifactToolkit.CopyPathSpecs;

public class DeployTask implements DeploymentTaskType {

	@Override
	public TaskResult execute(DeploymentTaskContext taskContext) throws TaskException {
		BuildLogger buildLogger = taskContext.getBuildLogger();

		ArtifactToUpload artifactToScp = ArtifactToolkit.getArtifactToScpFromConfig(taskContext);
		CopyPathSpecs pathSpecToCopy = ArtifactToolkit.getPathSpecToCopy(taskContext, artifactToScp);
		if (pathSpecToCopy == null) {
			String msg = "Unable to find artifact with id " + artifactToScp.getArtifactIdOrType() + ", called [" + artifactToScp.getName() + "] at the time this task was configured. The artifact definition or subscription has probably been removed from build.";
			buildLogger.addErrorLogEntry(taskContext.getBuildLogger().addErrorLogEntry(msg));
			return TaskResultBuilder.newBuilder(taskContext).failedWithError().build();
		}

		return execute(taskContext, pathSpecToCopy);
	}

	@NotNull
	private TaskResult execute(@NotNull CommonTaskContext taskContext, @NotNull CopyPathSpecs artifactToCopy) {
		BuildLogger buildLogger = taskContext.getBuildLogger();

		ConfigurationMap config = taskContext.getConfigurationMap();
		String host = config.get(UploadTaskConfigurator.HOST);
		long port = config.getAsLong(UploadTaskConfigurator.PORT);
		String channel = config.get(UploadTaskConfigurator.CHANNEL);
		String key = config.get(UploadTaskConfigurator.KEY);
		boolean uploadPoms = config.getAsBoolean(UploadTaskConfigurator.UPLOAD_POM);
		boolean skipUnparseableFiles = config.getAsBoolean(UploadTaskConfigurator.SKIP_UNPARSEABLE);
		String uploadType = config.get(UploadTaskConfigurator.CHILD_ARTIFACTS);

		PackageDroneClientAdapter client = new PackageDroneClientAdapter(host, port, channel, key, uploadPoms, skipUnparseableFiles, uploadType, buildLogger);

		TaskResultBuilder taskResultBuilder = TaskResultBuilder.newBuilder(taskContext);

		Set<File> failedToUpload = new HashSet<>();
		TaskResult taskResult = transferFiles(artifactToCopy, client, taskResultBuilder, buildLogger);

		if (!failedToUpload.isEmpty() || !TaskState.SUCCESS.equals(taskResult.getTaskState())) {
			buildLogger.addErrorLogEntry("Copy Failed. Some files were not uploaded successfully.");
			if (!failedToUpload.isEmpty()) {
				for (File file : failedToUpload) {
					buildLogger.addErrorLogEntry("'" + file.getAbsolutePath() + "' was not uploaded.");
				}
			}
			return taskResultBuilder.failedWithError().build();
		}
		return taskResult;
	}

	protected TaskResult transferFiles(CopyPathSpecs artifactToCopy, PackageDroneClientAdapter client, TaskResultBuilder taskResultBuilder, BuildLogger buildLogger) {
		try {
			client.uploadFiles(prepareListOfFilesForTransfer(artifactToCopy));
		} catch (ParserConfigurationException | SAXException | IOException | UploadException e) {
			buildLogger.addErrorLogEntry("Error uploading files", e);
			taskResultBuilder.failedWithError().build();
		}
		return taskResultBuilder.success().build();
	}

	protected static Set<File> prepareListOfFilesForTransfer(final CopyPathSpecs copyPathSpecs) {
		String antPathMatchString = copyPathSpecs.isAntPattern ? copyPathSpecs.copyPattern : convertToAntPaths(copyPathSpecs.copyPattern, copyPathSpecs.rootDirectory);

		final Set<File> filesToUpload = new HashSet<>();
		final FileVisitor namesVisitor = new FileVisitor(copyPathSpecs.rootDirectory) {
			@Override
			public void visitFile(final File file) {
				filesToUpload.add(file);
			}
		};

		try {
			namesVisitor.visitFilesThatMatch(antPathMatchString);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		return filesToUpload;
	}

	private static String convertToAntPaths(final String localPathMatchString, final File rootPath) {
		final String antPathMatchString;
		final File absoluteRootPath = rootPath.getAbsoluteFile();
		final Iterable<String> localPaths = Splitter.on(",").omitEmptyStrings().trimResults().split(localPathMatchString);
		antPathMatchString = Joiner.on(",").join(BambooIterables.stream(localPaths).map(s -> {
			if ("*".equals(s) || ("*" + File.separator).equals(s)) {
				return "**";
			}
			// the simplest case - regular file need an exact content, so we have to know
			// this is regular file;
			File file = new File(absoluteRootPath, s);
			if (s.contains("*") || !file.isFile()) {
				return s + "/**";
			}
			return s;
		}).collect(Collectors.toList()));
		return antPathMatchString;
	}
}