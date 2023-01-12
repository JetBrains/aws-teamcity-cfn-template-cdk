/*
 * Copyright 2000-2023 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.teamcity.aws.template.services.ecs.setup;

import jetbrains.teamcity.aws.template.services.efs.AwsTeamCityTemplateEfs;
import jetbrains.teamcity.aws.template.services.rds.AwsTeamCityTemplateRds;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static jetbrains.teamcity.aws.template.services.ecs.setup.SetupContainerParameters.createDatabaseSh;

public class SetupContainerTask {

    public static void addSetupContainer(@NotNull final Construct scope,
                                         @NotNull final ApplicationLoadBalancedFargateService loadBalancedFargateService,
                                         @NotNull final AwsTeamCityTemplateEfs teamcityTemplateEfs,
                                         @NotNull final AwsTeamCityTemplateRds teamcityTemplateRds,
                                         @NotNull final Map<String, String> envVarsMap) {

        LogGroup setupContainerLogGroup = LogGroup.Builder.create(scope, "SetupLogGroup")
                .retention(RetentionDays.ONE_WEEK)
                .build();

        ContainerDefinition containerDefinition = loadBalancedFargateService.getService().getTaskDefinition().addContainer("Setup", ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry("postgres:14.3"))
                .environment(envVarsMap)
                .entryPoint(Arrays.asList("/bin/sh", "-c"))
                .command(Collections.singletonList(
                        "apt-get update; apt-get install curl -y; mkdir -p /usr/local/lib/jdbc; curl https://jdbc.postgresql.org/download/postgresql-42.3.4.jar --output /usr/local/lib/jdbc/postgresql-42.3.4.jar; ls /usr/local/lib/jdbc; " + createDatabaseSh
                ))
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                        .logGroup(setupContainerLogGroup)
                        .streamPrefix("setup")
                        .build()))
                .essential(false)
                .build());
        containerDefinition.addMountPoints(teamcityTemplateEfs.getDataDirMountPoint(), teamcityTemplateEfs.getLogsDirMountPoint());
        containerDefinition.getNode().addDependency(teamcityTemplateRds.getDatabaseInstance());

        assert loadBalancedFargateService.getTaskDefinition().getDefaultContainer() != null;
        loadBalancedFargateService
                .getTaskDefinition()
                .getDefaultContainer()
                .addContainerDependencies(ContainerDependency.builder()
                        .container(containerDefinition)
                        .condition(ContainerDependencyCondition.COMPLETE)
                        .build());
    }
}
