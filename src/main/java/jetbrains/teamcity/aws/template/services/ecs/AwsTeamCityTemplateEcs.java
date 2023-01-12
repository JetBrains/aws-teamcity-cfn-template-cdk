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

package jetbrains.teamcity.aws.template.services.ecs;

import jetbrains.teamcity.aws.template.services.efs.AwsTeamCityTemplateEfs;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.Collections;

import static jetbrains.teamcity.aws.template.services.cloudfront.AwsTeamCityTemplateCloudFront.CUSTOM_HTTP_HEADER_FOR_ALB_RESTRICTION;
import static jetbrains.teamcity.aws.template.services.cloudfront.AwsTeamCityTemplateCloudFront.CUSTOM_HTTP_HEADER_FOR_ALB_RESTRICTION_VALUE;

public class AwsTeamCityTemplateEcs {

    private final ApplicationLoadBalancedFargateService myApplicationLoadBalancedFargateService;

    private final String startServerWithAgentCommand = "unzip /opt/teamcity/webapps/ROOT/update/buildAgent.zip -d /opt/teamcity/buildAgent; mv /opt/teamcity/buildAgent/conf/buildAgent.dist.properties /opt/teamcity/buildAgent/conf/buildAgent.properties; /opt/teamcity/bin/runAll.sh start; while ! tail -f /opt/teamcity/logs/teamcity-server.log ; do sleep 1 ; done";

    public AwsTeamCityTemplateEcs(@NotNull final Construct scope, @NotNull final Vpc vpc, @NotNull final AwsTeamCityTemplateEfs teamcityTemplateEfs) {
        Cluster cluster = Cluster.Builder.create(scope, "Cluster")
                .vpc(vpc)// creates Fargate ECS by default
                .build();

        //TeamCity server ALB balanced service
        myApplicationLoadBalancedFargateService = ApplicationLoadBalancedFargateService.Builder.create(scope, "Service")
                .cluster(cluster)
                .memoryLimitMiB(10240)
                .cpu(2048)
                .publicLoadBalancer(true)
                .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                        .family("TeamCityServer")
                        .containerName("TeamCity")
                        .image(ContainerImage.fromRegistry("jetbrains/teamcity-server"))
                        .entryPoint(Arrays.asList("/bin/sh", "-c"))
                        .command(Collections.singletonList(startServerWithAgentCommand))
                        .environment(Collections.singletonMap("TEAMCITY_HTTPS_PROXY_ENABLED", "true"))
                        .containerPort(8111)
                        .build())
                .build();

        assert myApplicationLoadBalancedFargateService.getTaskDefinition().getDefaultContainer() != null;

        //add mount points to the TC container
        myApplicationLoadBalancedFargateService.getTaskDefinition().getDefaultContainer().addMountPoints(teamcityTemplateEfs.getDataDirMountPoint(), teamcityTemplateEfs.getLogsDirMountPoint());

        myApplicationLoadBalancedFargateService.getTaskDefinition().addVolume(teamcityTemplateEfs.getDataDirVolume());
        myApplicationLoadBalancedFargateService.getTaskDefinition().addVolume(teamcityTemplateEfs.getLogsDirVolume());

        teamcityTemplateEfs.getEfs().getConnections().allowDefaultPortFrom(myApplicationLoadBalancedFargateService.getService().getConnections());

        teamcityTemplateEfs.getEfs().grant(myApplicationLoadBalancedFargateService.getTaskDefinition().getTaskRole(), "elasticfilesystem:ClientMount", "elasticfilesystem:ClientWrite");


        myApplicationLoadBalancedFargateService.getTargetGroup().configureHealthCheck(HealthCheck.builder()
                .path("/mnt/get/stateRevision")
                .build());

        addCustomHttpHeaderRequirement(scope);
    }

    public ApplicationLoadBalancedFargateService getApplicationLoadBalancedFargateService() {
        return myApplicationLoadBalancedFargateService;
    }

    private void addCustomHttpHeaderRequirement(@NotNull final Construct scope) {
        ApplicationListenerRule.Builder.create(scope, "CustomHeaderRuleForAlbRestriction")
                .listener(myApplicationLoadBalancedFargateService.getListener())
                .priority(1)
                .conditions(Collections.singletonList(
                        ListenerCondition.httpHeader(CUSTOM_HTTP_HEADER_FOR_ALB_RESTRICTION, Collections.singletonList(CUSTOM_HTTP_HEADER_FOR_ALB_RESTRICTION_VALUE))
                ))
                .action(ListenerAction.forward(Collections.singletonList(myApplicationLoadBalancedFargateService.getTargetGroup())))
                .build();

        CfnListener defaultListener = (CfnListener) myApplicationLoadBalancedFargateService.getListener().getNode().getDefaultChild();
        assert defaultListener != null;
        defaultListener.setDefaultActions(Collections.singletonList(
                CfnListener.ActionProperty.builder()
                        .type("fixed-response")
                        .fixedResponseConfig(CfnListener.FixedResponseConfigProperty.builder()
                                .statusCode("403")
                                .contentType("text/html")
                                .messageBody("Access denied")
                                .build())
                        .build())
        );
    }
}
