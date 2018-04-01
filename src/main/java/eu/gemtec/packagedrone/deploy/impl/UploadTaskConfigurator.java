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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.atlassian.bamboo.collections.ActionParametersMap;
import com.atlassian.bamboo.deployments.DeploymentTaskContextHelper;
import com.atlassian.bamboo.deployments.environments.Environment;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.artifact.ArtifactDefinition;
import com.atlassian.bamboo.plan.artifact.ArtifactDefinitionManager;
import com.atlassian.bamboo.plan.artifact.ImmutableArtifactDefinition;
import com.atlassian.bamboo.plan.artifact.ImmutableArtifactSubscription;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableJob;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;
import com.atlassian.bamboo.plugin.ArtifactDownloaderTaskConfigurationHelper;
import com.atlassian.bamboo.plugin.BambooPluginKeys;
import com.atlassian.bamboo.plugin.BambooPluginUtils;
import com.atlassian.bamboo.task.AbstractTaskConfigurator;
import com.atlassian.bamboo.task.TaskConfiguratorHelper;
import com.atlassian.bamboo.task.TaskContextHelper;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.task.TaskIdentifier;
import com.atlassian.bamboo.task.TaskPredicates;
import com.atlassian.bamboo.user.BambooAuthenticationContext;
import com.atlassian.bamboo.util.Narrow;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.webwork.util.WwSelectOption;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.BambooImport;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.message.I18nResolver;
import com.google.common.base.Predicate;


/**
 * Persistrs task configurations and loads task configuration when task is beeing edited.
 * 
 * @author Veselin Markov
 */
@Scanned
public class UploadTaskConfigurator extends AbstractTaskConfigurator {

	private static final Logger log = Logger.getLogger(UploadTaskConfigurator.class);
	static final String HOST = "host";
	static final String PORT = "port";
	static final String CHANNEL = "channel";
	static final String KEY = "key";
	static final String UPLOAD_POM = "uploadPom";
	static final String ARTIFACT_TO_UPLOAD = "artifactToScp";

	private I18nResolver textProvider;
	private CachedPlanManager cachedPlanManager;
	private ArtifactDefinitionManager artifactDefinitionManager;

	@Inject
	public UploadTaskConfigurator(@ComponentImport I18nResolver i18n, @ComponentImport CachedPlanManager cachedPlanManager,
			@ComponentImport ArtifactDefinitionManager artifactDefinitionManager, @BambooImport TaskConfiguratorHelper taskConfiguratorHelper,
			@BambooImport BambooAuthenticationContext bambooAuthenticationContext) {
		this.textProvider = i18n;
		this.cachedPlanManager = cachedPlanManager;
		this.artifactDefinitionManager = artifactDefinitionManager;
		super.taskConfiguratorHelper = taskConfiguratorHelper;
		super.bambooAuthenticationContext = bambooAuthenticationContext;
	}

	@Override
	public Map<String, String> generateTaskConfigMap(ActionParametersMap params, TaskDefinition previousTaskDefinition) {

		Map<String, String> generateTaskConfigMap = super.generateTaskConfigMap(params, previousTaskDefinition);
		taskConfiguratorHelper.populateTaskConfigMapWithActionParameters(generateTaskConfigMap, params, Arrays.asList(HOST, PORT, CHANNEL, KEY, UPLOAD_POM, ARTIFACT_TO_UPLOAD));
		return generateTaskConfigMap;
	}

	@Override
	public void populateContextForCreate(@NotNull Map<String, Object> context) {
		super.populateContextForCreate(context);
		addArtifactData(context, null);
	}

	@Override
	public void populateContextForEdit(@NotNull final Map<String, Object> context, @NotNull final TaskDefinition taskDefinition) {
		super.populateContextForEdit(context, taskDefinition);
		copyToContext(context, taskDefinition);
		addArtifactData(context, taskDefinition);
	}

	private void copyToContext(Map<String, Object> context, TaskDefinition taskDefinition) {
		for (String key : taskDefinition.getConfiguration().keySet()) {
			context.put(key, taskDefinition.getConfiguration().get(key));
		}
	}

	@Override
	public void validate(ActionParametersMap params, ErrorCollection errorCollection) {
		super.validate(params, errorCollection);
	}

	private void addArtifactData(Map<String, Object> context, @Nullable TaskDefinition definitionOfTaskBeingEdited) {
		final ImmutableJob job = Narrow.reinterpret(TaskContextHelper.getPlan(context), ImmutableJob.class);

		final List<WwSelectOption> artifactsToScp = new ArrayList<>();

		final String sharedArtifactsGroup = getI18nBean().getText("pd.deploy.view.artifacts");
		final String localDirGroup = textProvider.getText("pd.deploy.view.files.in.working.directory");

		if (job != null) {
			// it's a job. It has subscriptions and definitions
			for (ImmutableArtifactSubscription artifactSubscription : job.getArtifactSubscriptions())
				artifactsToScp.add(new WwSelectOption(artifactSubscription.getName(), sharedArtifactsGroup, ArtifactToUpload.from(artifactSubscription).toString()));

			addArtifactsFromDownloaderTasks(job.getBuildDefinition().getTaskDefinitions(), definitionOfTaskBeingEdited, artifactsToScp);

			for (ImmutableArtifactDefinition artifactDefinition : job.getArtifactDefinitions())
				artifactsToScp.add(new WwSelectOption(artifactDefinition.getName(), localDirGroup, ArtifactToUpload.from(artifactDefinition).toString()));
		} else {
			// it's an environment
			final Environment environment = DeploymentTaskContextHelper.getEnvironment(context);
			addArtifactsFromDownloaderTasks(environment.getTaskDefinitions(), definitionOfTaskBeingEdited, artifactsToScp);
		}

		context.put("artifactsToScp", artifactsToScp);
	}

	@SuppressWarnings("deprecation")
	private void addArtifactsFromDownloaderTasks(List<TaskDefinition> taskDefinitions, @Nullable TaskDefinition definitionOfTaskBeingEdited, List<WwSelectOption> artifactsToScp) {

		Predicate<TaskIdentifier> fromDownloader = TaskPredicates.isTaskDefinitionPluginKeyEqual(BambooPluginKeys.ARTIFACT_DOWNLOAD_TASK_MODULE_KEY);
		Predicate<TaskDefinition> taskEnabled = TaskPredicates.isTaskEnabled();
		@SuppressWarnings("unchecked")
		Iterable<TaskDefinition> tasks = BambooPluginUtils.filterTasks(taskDefinitions, new Predicate[] { fromDownloader, taskEnabled });

		for (TaskDefinition task : tasks) {
			if (task.equals(definitionOfTaskBeingEdited)) {
				break;
			}
			final Map<String, String> taskConfiguration = task.getConfiguration();
			final String sourcePlanKey = checkNotNull(ArtifactDownloaderTaskConfigurationHelper.getSourcePlanKey(taskConfiguration),
					"Source plan key not found in task configuration");
			final PlanKey typedSourcePlanKey = PlanKeys.getPlanKey(sourcePlanKey);
			final ImmutablePlan plan = checkNotNull(cachedPlanManager.getPlanByKey(typedSourcePlanKey), "Plan " + typedSourcePlanKey + " not found");
			final String artifactsFromOtherPlans = textProvider.getText("pd.deploy.view.other.plan.artifacts", plan.getName());

			for (String artifactIdKey : ArtifactDownloaderTaskConfigurationHelper.getArtifactKeys(taskConfiguration)) {
				long artifactId = Long.parseLong(taskConfiguration.get(artifactIdKey));
				int transferId = ArtifactDownloaderTaskConfigurationHelper.getIndexFromKey(artifactIdKey);

				String artifactName;
				String selectValue;
				if (artifactId == -1) {
					artifactName = textProvider.getText("pd.deploy.view.plan.all.artifacts", sourcePlanKey);
					selectValue = ArtifactToUpload.fromTransferTask(ArtifactToUpload.ARTIFACT_ID_ALL_ARTIFACTS, task, transferId, artifactName).toString();
				} else {
					final ArtifactDefinition artifactDefinition = artifactDefinitionManager.findArtifactDefinition(artifactId);
					if (artifactDefinition == null) {
						log.warn("Artifact defintion " + artifactId + " not found.");
						continue;
					}
					artifactName = textProvider.getText("pd.deploy.view.plan.one.artifact", sourcePlanKey, artifactDefinition.getName());
					selectValue = ArtifactToUpload.fromTransferTask(artifactDefinition.getId(), task, transferId, artifactDefinition.getName()).toString();
				}
				artifactsToScp.add(new WwSelectOption(artifactName, artifactsFromOtherPlans, selectValue));
			}
		}
	}

}
