/*
 * Copyright 2017 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.cloud.scheduler.spi.kubernetes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.BatchV1beta1Api;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1JobSpec;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PodSpec;
import io.kubernetes.client.models.V1PodTemplateSpec;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.V1beta1CronJob;
import io.kubernetes.client.models.V1beta1CronJobList;
import io.kubernetes.client.models.V1beta1CronJobSpec;
import io.kubernetes.client.models.V1beta1JobTemplateSpec;

import org.springframework.cloud.scheduler.spi.core.ScheduleInfo;
import org.springframework.cloud.scheduler.spi.core.ScheduleRequest;
import org.springframework.cloud.scheduler.spi.core.Scheduler;
import org.springframework.cloud.scheduler.spi.core.SchedulerPropertyKeys;

/**
 * A Kubernetes implementation of the Scheduler interface.
 *
 * @author Glenn Renfro
 */
public class KubernetesScheduler implements Scheduler {

	private static final String API_VERSION = "batch/v1beta1";

	private static final String APP_NAME =
			"org.springframework.cloud.spi.scheduler.kuberntetes.appName";

	private BatchV1beta1Api batchV1beta1Api;

	private KubernetesSchedulerProperties properties;

	public KubernetesScheduler(BatchV1beta1Api batchV1beta1Api, KubernetesSchedulerProperties properties) {
		this.batchV1beta1Api = batchV1beta1Api;
		this.properties = properties;
	}

	@Override
	public void schedule(ScheduleRequest scheduleRequest) {
		V1beta1CronJob body = new V1beta1CronJob(); // V2alpha1CronJob |
		body.setApiVersion(API_VERSION);
		body.setKind("CronJob");
		body.setMetadata(createMetadata(scheduleRequest));
		body.setSpec(createCronJobSpec(scheduleRequest));
		String pretty = "true"; // String | If 'true', then the output is pretty printed.
		try {
			batchV1beta1Api.createNamespacedCronJob(properties.getNamedSpace(), body, pretty);
		}
		catch (ApiException e) {
			throw new IllegalStateException("Exception when calling BatchV1Beta1Api#createNamespacedCronJob", e);
		}
	}

	@Override
	public void unschedule(String scheduleName) {
		V1DeleteOptions body = new V1DeleteOptions();
		body.setApiVersion(API_VERSION);
		body.setGracePeriodSeconds((long)properties.getGracePeriodSeconds());
		body.setOrphanDependents(false);
		try {
			V1Status result = batchV1beta1Api.deleteNamespacedCronJob(scheduleName,
					properties.getNamedSpace(), body, null, properties.getGracePeriodSeconds(), false, null);

			System.out.println(result);
		}
		catch (ApiException e) {
			throw new IllegalStateException("Exception when calling BatchV1Beta1Api#deleteNamespacedCronJob", e);
		}
	}

	@Override
	public List<ScheduleInfo> list(String taskDefinitionName) {
		try {
			V1beta1CronJobList result = batchV1beta1Api.listCronJobForAllNamespaces(null, null, null, APP_NAME  + " = " + taskDefinitionName, null, null, null, null, null);
			return createScheduleList(result);

		}
		catch (ApiException e) {
			throw new IllegalStateException("Exception when calling BatchV1Beta1Api#listCronJobForAllNamespaces", e);
		}
	}

	@Override
	public List<ScheduleInfo> list() {
		try {
			V1beta1CronJobList result = batchV1beta1Api.listCronJobForAllNamespaces(null, null, null, null, null, null, null, null, null);
			return createScheduleList(result);
		}
		catch (ApiException e) {
			throw new IllegalStateException("Exception when calling BatchV1Beta1Api#listCronJobForAllNamespaces", e);
		}
	}

	private List<ScheduleInfo> createScheduleList(V1beta1CronJobList cronJobList) {
		List<ScheduleInfo> scheduleInfos = new ArrayList<>();

		for (V1beta1CronJob item : cronJobList.getItems()) {
			System.out.println(item.getMetadata().getName());
			ScheduleInfo scheduleInfo = new ScheduleInfo();
			scheduleInfo.setScheduleName(item.getMetadata().getName());
			scheduleInfo.setTaskDefinitionName(item.getMetadata().getLabels().get(APP_NAME));

			Map<String, String> properties = new HashMap<>();
			properties.put(SchedulerPropertyKeys.CRON_EXPRESSION, item.getSpec().getSchedule());
			scheduleInfo.setScheduleProperties(properties);
			scheduleInfos.add(scheduleInfo);
		}
		return scheduleInfos;
	}

	private V1ObjectMeta createMetadata(ScheduleRequest request) {
		V1ObjectMeta metadata = new V1ObjectMeta();
		metadata.setName(request.getScheduleName().toLowerCase());
		metadata.setLabels(Collections.singletonMap(APP_NAME, request.getDefinition().getName()));
		return metadata;
	}

	private V1beta1CronJobSpec createCronJobSpec(ScheduleRequest request) {
		V1beta1CronJobSpec cronJobSpec = new V1beta1CronJobSpec();
		cronJobSpec.setSchedule(request.getSchedulerProperties().get(SchedulerPropertyKeys.CRON_EXPRESSION));
		cronJobSpec.setJobTemplate(createJobTemplate(request));
		return cronJobSpec;
	}

	private V1beta1JobTemplateSpec createJobTemplate(ScheduleRequest request) {
		V1beta1JobTemplateSpec templateSpec = new V1beta1JobTemplateSpec();
		templateSpec.setSpec(createJobSpec(request));
		return templateSpec;
	}

	private V1JobSpec createJobSpec(ScheduleRequest request) {
		V1JobSpec jobSpec = new V1JobSpec();
		jobSpec.setTemplate(createPodTemplateSpec(request));
		return jobSpec;
	}

	private V1PodTemplateSpec createPodTemplateSpec(ScheduleRequest request) {
		V1PodTemplateSpec podTemplateSpec = new V1PodTemplateSpec();
		podTemplateSpec.setSpec(createPodSpec(request));
		return podTemplateSpec;
	}

	private V1PodSpec createPodSpec(ScheduleRequest request) {
		V1PodSpec v1PodSpec = new V1PodSpec();
		V1Container container = new V1Container();
		container.setName("cloudschedule-" + request.getScheduleName().toLowerCase());
		try {
			container.setImage(request.getResource().getURI().getSchemeSpecificPart());
		}
		catch (IOException e) {
			throw new IllegalStateException("Invalid Url for Docker Image", e);
		}
		request.getCommandlineArguments().stream().forEach(arg -> container.addArgsItem(arg));

		v1PodSpec.setContainers(Collections.singletonList(container));
		v1PodSpec.setRestartPolicy("OnFailure");
		return v1PodSpec;
	}

}
