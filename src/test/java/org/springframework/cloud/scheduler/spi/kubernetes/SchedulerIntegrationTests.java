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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceList;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.deployer.resource.docker.DockerResource;
import org.springframework.cloud.deployer.spi.core.AppDefinition;
import org.springframework.cloud.scheduler.spi.core.ScheduleInfo;
import org.springframework.cloud.scheduler.spi.core.ScheduleRequest;
import org.springframework.cloud.scheduler.spi.core.Scheduler;
import org.springframework.cloud.scheduler.spi.core.SchedulerPropertyKeys;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {KubernetesSchedulerAutoConfiguration.class})
public class SchedulerIntegrationTests {

	private static final String DEFAULT_CRON_JOB_NAME = "testjob";

	@Autowired
	Scheduler scheduler;

	@Autowired
	CoreV1Api coreAPI;

	private String mySqlClusterIP;

	private List<String> jobsToRemove;

	@Before
	public void setup() {
		jobsToRemove = new ArrayList<>();
		try {
			V1ServiceList list = coreAPI.listNamespacedService("default", "true", null, null, null, null, null, null, null, null);
			List<V1Service> mysqlServiceList = list.getItems().stream()
					.filter(item -> item.getMetadata().getName().equals("mysql"))
					.collect(Collectors.toList());
			assertThat(mysqlServiceList.size()).isEqualTo(1);
			this.mySqlClusterIP = mysqlServiceList.get(0).getSpec().getClusterIP();
		}
		catch (ApiException e) {
			e.printStackTrace();
			throw new IllegalStateException(e);
		}
	}

	@After
	public void teardown() {
		unscheduleTasks();
	}

	@Rule
	public TestName name = new TestName();

	@Test
	public void createSingleJob() throws Exception {
		ScheduleRequest request = getRequest("testschedulesingleJob");
		scheduler.schedule(request);
		Thread.sleep(100000);
		List<ScheduleInfo> schedules = scheduler.list();
		assertThat(schedules.size()).isEqualTo(1);
		assertThat(schedules.get(0).getScheduleName()).isEqualTo(request.getScheduleName().toLowerCase());
		assertThat(schedules.get(0).getTaskDefinitionName()).isEqualTo(request.getDefinition().getName());
		assertThat(schedules.get(0).getScheduleProperties()
				.get("spring.cloud.scheduler.expression"))
				.isEqualTo(request.getSchedulerProperties()
						.get("spring.cloud.scheduler.expression"));
	}

	@Test
	public void testListJobs() throws Exception {
		final int TEST_SIZE = 5;
		List<ScheduleRequest> requests = new ArrayList<>(TEST_SIZE);
		for (int i = 0; i < TEST_SIZE; i++) {
			requests.add(getRequest("testschedule" + i));
			scheduler.schedule(requests.get(i));

		}

		List<ScheduleInfo> schedules = scheduler.list();
		assertThat(schedules.size()).isEqualTo(TEST_SIZE);
		for (int i = 0; i < 5; i++) {
			assertThat(schedules.get(i).getScheduleName()).isEqualTo(requests.get(i).getScheduleName());
			assertThat(schedules.get(i).getTaskDefinitionName()).isEqualTo(requests.get(i).getDefinition().getName());
			assertThat(schedules.get(i).getScheduleProperties()
					.get("spring.cloud.scheduler.expression"))
					.isEqualTo(requests.get(i).getSchedulerProperties()
							.get("spring.cloud.scheduler.expression"));
		}
	}

	@Test
	public void testListJobsFiltered() throws Exception {
		final int TEST_SIZE = 5;
		List<ScheduleRequest> requests = new ArrayList<>(TEST_SIZE);
		for (int i = 0; i < TEST_SIZE; i++) {
			requests.add(getRequest("timestamp-task" + i, "testschedule" + i));
			scheduler.schedule(requests.get(i));

		}

		List<ScheduleInfo> schedules = scheduler.list("timestamp-task1");
		assertThat(schedules.size()).isEqualTo(1);
			assertThat(schedules.get(0).getScheduleName()).isEqualTo(requests.get(1).getScheduleName());
			assertThat(schedules.get(0).getTaskDefinitionName()).isEqualTo(requests.get(1).getDefinition().getName());
			assertThat(schedules.get(0).getScheduleProperties()
					.get("spring.cloud.scheduler.expression"))
					.isEqualTo(requests.get(1).getSchedulerProperties()
							.get("spring.cloud.scheduler.expression"));
	}

	private ScheduleRequest getRequest(String namePrefix) {
		return getRequest("timestamp-task", namePrefix);
	}

	private ScheduleRequest getRequest(String taskDefinitionName, String namePrefix) {
		AppDefinition definition = new AppDefinition(taskDefinitionName, null);
		Map<String, String> scheduleProperties = new HashMap<>();
		scheduleProperties.put(SchedulerPropertyKeys.CRON_EXPRESSION, "*/1 * * * *");
		Map<String, String> deploymentProperties = new HashMap<>();
		ScheduleRequest request = new ScheduleRequest(definition,
				scheduleProperties, deploymentProperties, getDbCommandLineArgs(), namePrefix + DEFAULT_CRON_JOB_NAME,
				new DockerResource("cppwfs/timestamp-task"));
		jobsToRemove.add(request.getScheduleName());
		return request;
	}

	private void unscheduleTasks() {
		jobsToRemove.forEach(scheduleName -> {
			scheduler.unschedule(scheduleName.toLowerCase());
		});
	}

	private String randomName() {
		return name.getMethodName() + "-" + UUID.randomUUID().toString();
	}

	private List<String> getDbCommandLineArgs() {
		List<String> commandLineArgs = new ArrayList<>(5);
		commandLineArgs.add("--spring.datasource.username=root");
		commandLineArgs.add("--spring.datasource.url=jdbc:mysql://" + this.mySqlClusterIP + ":3306/mysql");
		commandLineArgs.add("--spring.cloud.task.name=tstamp1");
		commandLineArgs.add("--spring.datasource.driverClassName=org.mariadb.jdbc.Driver");
		commandLineArgs.add("--spring.datasource.password=yourpassword");
		return commandLineArgs;
	}
}
