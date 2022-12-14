package jetbrains.teamcity.aws.template.services.ecs;

import jetbrains.teamcity.aws.template.services.efs.AwsTeamCityTemplateEfs;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.Collections;

public class AwsTeamCityTemplateEcs {

    private final ApplicationLoadBalancedFargateService myApplicationLoadBalancedFargateService;

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
                        .command(Collections.singletonList("/run-services.sh"))
                        //TODO check why TC behaves strangely with CloudFront when using this option
//                        .environment(Collections.singletonMap("TEAMCITY_HTTPS_PROXY_ENABLED", "true"))
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
    }

    public ApplicationLoadBalancedFargateService getApplicationLoadBalancedFargateService() {
        return myApplicationLoadBalancedFargateService;
    }
}
