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

package jetbrains.teamcity.aws.template;

import jetbrains.teamcity.aws.template.services.cloudfront.AwsTeamCityTemplateCloudFront;
import jetbrains.teamcity.aws.template.services.ecs.AwsTeamCityTemplateEcs;
import jetbrains.teamcity.aws.template.services.ecs.setup.SetupContainerParameters;
import jetbrains.teamcity.aws.template.services.ecs.setup.SetupContainerTask;
import jetbrains.teamcity.aws.template.services.efs.AwsTeamCityTemplateEfs;
import jetbrains.teamcity.aws.template.services.rds.AwsTeamCityTemplateRds;
import jetbrains.teamcity.aws.template.services.vpc.AwsTeamCityTemplateVpc;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.constructs.Construct;

import java.util.Map;

public class AwsTeamcityCfnTemplateCdkStack extends Stack {

    public AwsTeamcityCfnTemplateCdkStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        Vpc vpc = AwsTeamCityTemplateVpc.buildVpc(this);

        //EFS
        AwsTeamCityTemplateEfs teamcityTemplateEfs = new AwsTeamCityTemplateEfs(this, vpc);

        //ECS
        AwsTeamCityTemplateEcs teamcityTemplateEcs = new AwsTeamCityTemplateEcs(this, vpc, teamcityTemplateEfs);
        ApplicationLoadBalancedFargateService loadBalancedFargateService = teamcityTemplateEcs.getApplicationLoadBalancedFargateService();

        //CloudFront
        AwsTeamCityTemplateCloudFront teamcityTemplateCloudFront = new AwsTeamCityTemplateCloudFront(this, loadBalancedFargateService);

        //RDS database
        AwsTeamCityTemplateRds teamcityTemplateRds = new AwsTeamCityTemplateRds(this, vpc, loadBalancedFargateService);

        //Setup Container Task
        Map<String, String> setupContainerParams = SetupContainerParameters.getEnvVarParameters(teamcityTemplateRds);
        SetupContainerTask.addSetupContainer(this,
                loadBalancedFargateService,
                teamcityTemplateEfs,
                teamcityTemplateRds,
                setupContainerParams
        );

        new CfnOutput(this, "TeamCityConnectionUrl", CfnOutputProps.builder()
                .value(teamcityTemplateCloudFront.getCloudFrontDistribution().getDistributionDomainName())
                .description("TeamCity CloudFront domain name")
                .build());
    }
}
