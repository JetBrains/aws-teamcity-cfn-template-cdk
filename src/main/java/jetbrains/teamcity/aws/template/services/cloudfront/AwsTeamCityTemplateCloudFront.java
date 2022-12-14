package jetbrains.teamcity.aws.template.services.cloudfront;

import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.cloudfront.*;
import software.amazon.awscdk.services.cloudfront.origins.LoadBalancerV2Origin;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.constructs.Construct;

public class AwsTeamCityTemplateCloudFront {

    private final Distribution myCloudFrontDistribution;

    public AwsTeamCityTemplateCloudFront(@NotNull final Construct scope, @NotNull final ApplicationLoadBalancedFargateService loadBalancedFargateService) {
        LoadBalancerV2Origin albOrigin = LoadBalancerV2Origin.Builder.create(loadBalancedFargateService.getLoadBalancer())
                .connectionAttempts(3)
                .connectionTimeout(Duration.seconds(5))
                .readTimeout(Duration.seconds(45))
                .keepaliveTimeout(Duration.seconds(45))
                .protocolPolicy(OriginProtocolPolicy.HTTP_ONLY) //unencrypted between alb and cloudfront
                .build();

        myCloudFrontDistribution = Distribution.Builder.create(scope, "TeamCityCloudFrontDistribution")
                .defaultBehavior(BehaviorOptions.builder()
                        .origin(albOrigin)
                        .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                        .allowedMethods(AllowedMethods.ALLOW_ALL)
                        .build())
                .build();
    }

    public Distribution getCloudFrontDistribution() {
        return myCloudFrontDistribution;
    }
}
