package jetbrains.teamcity.aws.template;

import jetbrains.teamcity.aws.template.services.cloudfront.AwsTeamCityTemplateCloudFront;
import jetbrains.teamcity.aws.template.services.ecs.AwsTeamCityTemplateEcs;
import jetbrains.teamcity.aws.template.services.efs.AwsTeamCityTemplateEfs;
import jetbrains.teamcity.aws.template.services.rds.AwsTeamCityTemplateRds;
import jetbrains.teamcity.aws.template.services.vpc.AwsTeamCityTemplateVpc;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static jetbrains.teamcity.aws.template.services.ecs.setup.SetupContainerCommands.createDatabaseSh;

public class AwsTeamcityCfnTemplateCdkStack extends Stack {

    public AwsTeamcityCfnTemplateCdkStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        Vpc vpc = AwsTeamCityTemplateVpc.buildVpc(this);

        //EFS
        AwsTeamCityTemplateEfs teamcityTemplateEfs = new AwsTeamCityTemplateEfs(this, vpc);

        //Passwords
        CfnParameter pgPass = CfnParameter.Builder.create(this, "MasterDatabasePassword")
                .type("String")
                .description("MasterDatabasePassword")
                .build();

        //ECS
        AwsTeamCityTemplateEcs teamcityTemplateEcs = new AwsTeamCityTemplateEcs(this, vpc, teamcityTemplateEfs);
        ApplicationLoadBalancedFargateService loadBalancedFargateService = teamcityTemplateEcs.getApplicationLoadBalancedFargateService();

        //CloudFront
        AwsTeamCityTemplateCloudFront teamcityTemplateCloudFront = new AwsTeamCityTemplateCloudFront(this, loadBalancedFargateService);

        //RDS database
        AwsTeamCityTemplateRds teamcityTemplateRds = new AwsTeamCityTemplateRds(this, vpc, loadBalancedFargateService, new SecretValue(pgPass.getValueAsString()));


        //Setup container parameters
        Map<String, String> envVarsMap = new HashMap<>();
        envVarsMap.put("PGHOST", teamcityTemplateRds.getDatabaseInstance().getInstanceEndpoint().getHostname());
        envVarsMap.put("PGPORT", "5432");
        envVarsMap.put("PGUSER", AwsTeamCityTemplateRds.DATABASE_USERNAME);
        envVarsMap.put("PGPASSWORD", pgPass.getValueAsString());

        envVarsMap.put("DB", AwsTeamCityTemplateRds.DATABASE_NAME);
        envVarsMap.put("DATADIR", "/data/teamcity_server/datadir");
        envVarsMap.put("LOGSDIR", "/opt/teamcity/logs");
        envVarsMap.put("TEAMCITY_SERVER_OPTS", "-Dteamcity.startup.maintenance=false");
        //Setup container parameters ^


        //Setup container task
        LogGroup setupContainerLogGroup = LogGroup.Builder.create(this, "SetupLogGroup")
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
        //Setup container task ^


        new CfnOutput(this, "TeamCityConnectionUrl", CfnOutputProps.builder()
                .value(teamcityTemplateCloudFront.getCloudFrontDistribution().getDistributionDomainName())
                .description("TeamCity CloudFront domain name")
                .build());
    }
}
