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

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.apis.BatchV1beta1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.util.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates a {@link KubernetesAppScheduler}.
 *
 * @author Glenn Renfro
 */
@Configuration
public class KubernetesSchedulerAutoConfiguration {

	@Bean
	public BatchV1beta1Api api() {
		try {
			ApiClient client = Config.defaultClient();
			io.kubernetes.client.Configuration.setDefaultApiClient(client);
		}
		catch (IOException ioe) {
			throw new IllegalStateException(ioe);
		}
		return new BatchV1beta1Api();
	}

	@Bean
	public CoreV1Api coreV1Api() {
		try {
			ApiClient client = Config.defaultClient();
			io.kubernetes.client.Configuration.setDefaultApiClient(client);
		}
		catch (IOException ioe) {
			throw new IllegalStateException(ioe);
		}
		return new CoreV1Api();
	}

	@Bean
	public KubernetesAppScheduler scheduler(BatchV1beta1Api batchV1beta1Api) {
		return new KubernetesAppScheduler(batchV1beta1Api);
	}
}