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

package jetbrains.teamcity.aws.template.services.cloudfront;

import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.cloudfront.*;
import software.amazon.awscdk.services.cloudfront.origins.LoadBalancerV2Origin;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.constructs.Construct;

import java.util.Collections;
import java.util.UUID;

public class AwsTeamCityTemplateCloudFront {

    public static final String CUSTOM_HTTP_HEADER_FOR_ALB_RESTRICTION = "AlbRestrictionHeader";
    public static final String CUSTOM_HTTP_HEADER_FOR_ALB_RESTRICTION_VALUE = UUID.randomUUID().toString();

    private final Distribution myCloudFrontDistribution;

    public AwsTeamCityTemplateCloudFront(@NotNull final Construct scope, @NotNull final ApplicationLoadBalancedFargateService loadBalancedFargateService) {
        LoadBalancerV2Origin albOrigin = LoadBalancerV2Origin.Builder.create(loadBalancedFargateService.getLoadBalancer())
                .connectionAttempts(3)
                .connectionTimeout(Duration.seconds(5))
                .readTimeout(Duration.seconds(45))
                .customHeaders(Collections.singletonMap(CUSTOM_HTTP_HEADER_FOR_ALB_RESTRICTION, CUSTOM_HTTP_HEADER_FOR_ALB_RESTRICTION_VALUE))
                .keepaliveTimeout(Duration.seconds(45))
                .protocolPolicy(OriginProtocolPolicy.HTTP_ONLY) //unencrypted between alb and cloudfront
                .build();

        myCloudFrontDistribution = Distribution.Builder.create(scope, "TeamCityCloudFrontDistribution")
                .defaultBehavior(BehaviorOptions.builder()
                        .origin(albOrigin)
                        .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                        .allowedMethods(AllowedMethods.ALLOW_ALL)
                        .originRequestPolicy(OriginRequestPolicy.ALL_VIEWER)
                        .cachePolicy(CachePolicy.CACHING_DISABLED)
                        .build())
                .build();
    }

    public Distribution getCloudFrontDistribution() {
        return myCloudFrontDistribution;
    }
}
