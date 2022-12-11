package jetbrains.teamcity.aws.template;

import jetbrains.teamcity.aws.template.vpc.AwsTeamcityTemplateVpc;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.cloudfront.*;
import software.amazon.awscdk.services.cloudfront.origins.LoadBalancerV2Origin;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.Collections;

public class AwsTeamcityCfnTemplateCdkStack extends Stack {

    public AwsTeamcityCfnTemplateCdkStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        Vpc vpc = AwsTeamcityTemplateVpc.buildVpc(this);

        ApplicationLoadBalancedFargateService loadBalancedFargateService = ApplicationLoadBalancedFargateService.Builder.create(this, "Service")
                .vpc(vpc)
                .memoryLimitMiB(10240)
                .cpu(2048)
                .publicLoadBalancer(true)
                .taskImageOptions(ApplicationLoadBalancedTaskImageOptions.builder()
                        .family("TeamCityServer")
                        .containerName("TeamCity")
                        .image(ContainerImage.fromRegistry("jetbrains/teamcity-server"))
                        .entryPoint(Arrays.asList("/bin/sh", "-c"))
                        .command(Collections.singletonList("/run-services.sh"))
                        .containerPort(8111)
                        .build())
                .build();

        loadBalancedFargateService.getTargetGroup().configureHealthCheck(HealthCheck.builder()
                .path("/mnt/get/stateRevision")
                .build());



        LoadBalancerV2Origin albOrigin = LoadBalancerV2Origin.Builder.create(loadBalancedFargateService.getLoadBalancer())
                .connectionAttempts(3)
                .connectionTimeout(Duration.seconds(5))
                .readTimeout(Duration.seconds(45))
                .keepaliveTimeout(Duration.seconds(45))
                .protocolPolicy(OriginProtocolPolicy.HTTP_ONLY) //unencrypted between alb and cloudfront
                .build();

        Distribution.Builder.create(this, "TeamCityCloudFrontDistribution")
                .defaultBehavior(BehaviorOptions.builder()
                        .origin(albOrigin)
                        .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                        .allowedMethods(AllowedMethods.ALLOW_ALL)
                        .build())
                .build();
    }
}
