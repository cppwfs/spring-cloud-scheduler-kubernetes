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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Contains the properties that are used to configure the Scheduler.
 *
 * @author Glenn Renfro
 */
@Validated
@ConfigurationProperties(prefix = KubernetesSchedulerProperties.KUBERNETES_PROPERTIES)
public class KubernetesSchedulerProperties {

	/**
	 * Top level prefix for Cloud Foundry related configuration properties.
	 */
	public static final String KUBERNETES_PROPERTIES = "spring.cloud.scheduler.kubernetes";

	private String namedSpace = "default";

	/**
	 * The duration in seconds before the object should be deleted.
	 */
	private int gracePeriodSeconds;

	public int getGracePeriodSeconds() {
		return gracePeriodSeconds;
	}

	public void setGracePeriodSeconds(int gracePeriodSeconds) {
		this.gracePeriodSeconds = gracePeriodSeconds;
	}

	public String getNamedSpace() {
		return namedSpace;
	}

	public void setNamedSpace(String namedSpace) {
		this.namedSpace = namedSpace;
	}
}
