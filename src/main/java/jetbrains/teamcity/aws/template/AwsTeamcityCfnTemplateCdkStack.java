package jetbrains.teamcity.aws.template;

import jetbrains.teamcity.aws.template.vpc.AwsTeamcityTemplateVpc;
import software.amazon.awscdk.*;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.cloudfront.*;
import software.amazon.awscdk.services.cloudfront.origins.LoadBalancerV2Origin;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Volume;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.efs.*;
import software.amazon.awscdk.services.efs.FileSystem;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

import java.util.*;

import static jetbrains.teamcity.aws.template.ecs.setup.SetupContainerCommands.createDatabaseSh;

public class AwsTeamcityCfnTemplateCdkStack extends Stack {

    public AwsTeamcityCfnTemplateCdkStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        Vpc vpc = AwsTeamcityTemplateVpc.buildVpc(this);



        FileSystem fileSystem = FileSystem.Builder.create(this, "MyEfsFileSystem")
                .vpc(vpc)
                .lifecyclePolicy(LifecyclePolicy.AFTER_14_DAYS) // files are not transitioned to infrequent access (IA) storage by default
                .performanceMode(PerformanceMode.GENERAL_PURPOSE) // default
                .throughputMode(ThroughputMode.BURSTING)
                .outOfInfrequentAccessPolicy(OutOfInfrequentAccessPolicy.AFTER_1_ACCESS)
                .removalPolicy(RemovalPolicy.DESTROY) // otherwise stateful resources will not be deleted when Clfn stack is deleted
                .build();

        AccessPoint dataDirAccessPoint = AccessPoint.Builder.create(this, "TeamCity data")
                .posixUser(
                        PosixUser.builder()
                                .gid("1000")
                                .uid("1000")
                                .build()
                )
                .createAcl(Acl.builder()
                        .ownerGid("1000")
                        .ownerUid("1000")
                        .permissions("0755")
                        .build())
                .path("/teamcity/data")
                .fileSystem(fileSystem)
                .build();
        dataDirAccessPoint.getNode().addDependency(fileSystem);

        AccessPoint logsDirAccessPoint = AccessPoint.Builder.create(this, "TeamCity logs")
                .posixUser(
                        PosixUser.builder()
                                .gid("1000")
                                .uid("1000")
                                .build()
                )
                .createAcl(Acl.builder()
                        .ownerGid("1000")
                        .ownerUid("1000")
                        .permissions("0755")
                        .build())
                .path("/teamcity/logs")
                .fileSystem(fileSystem)
                .build();
        logsDirAccessPoint.getNode().addDependency(fileSystem);


        Volume dataDirVolume = Volume.builder()
                .name("Data")
                .efsVolumeConfiguration(EfsVolumeConfiguration.builder()
                        .fileSystemId(fileSystem.getFileSystemId())
                        .transitEncryption("ENABLED")
                        .authorizationConfig(AuthorizationConfig.builder()
                                .accessPointId(dataDirAccessPoint.getAccessPointId())
                                .iam("ENABLED")
                                .build())
                        .build())
                .build();
        Volume logsVolume = Volume.builder()
                .name("Logs")
                .efsVolumeConfiguration(EfsVolumeConfiguration.builder()
                        .fileSystemId(fileSystem.getFileSystemId())
                        .transitEncryption("ENABLED")
                        .authorizationConfig(AuthorizationConfig.builder()
                                .accessPointId(logsDirAccessPoint.getAccessPointId())
                                .iam("ENABLED")
                                .build())
                        .build())
                .build();


        MountPoint dataDirMountPoint = MountPoint.builder()
                .containerPath("/data/teamcity_server/datadir")
                .readOnly(false)
                .sourceVolume(dataDirVolume.getName())
                .build();
        MountPoint logsMountPoint = MountPoint.builder()
                .containerPath("/opt/teamcity/logs")
                .readOnly(false)
                .sourceVolume(logsVolume.getName())
                .build();















        Cluster cluster = Cluster.Builder.create(this, "Cluster")
                .vpc(vpc)// creates Fargate ECS by default
                .build();

//        FargateTaskDefinition fargateTaskDefinition = FargateTaskDefinition.Builder.create(this, "SetupTeamCityTask")
//                .memoryLimitMiB(1024)
//                .cpu(256)
//                .volumes(Arrays.asList(dataDirVolume, logsVolume))
//                .build();


        CfnParameter pgHost = CfnParameter.Builder.create(this, "PGHOST")
                .type("String")
                .description("PGHOST")
                .build();
        CfnParameter pgPass = CfnParameter.Builder.create(this, "PGPASSWORD")
                .type("String")
                .description("PGPASSWORD")
                .build();


        Map<String, String> envVarsMap = new HashMap<>();
        envVarsMap.put("PGHOST", pgHost.getValueAsString());
        envVarsMap.put("PGPORT", "5432");
        envVarsMap.put("PGUSER", "postgres");
        envVarsMap.put("PGPASSWORD", pgPass.getValueAsString());

        envVarsMap.put("DB", "teamcity");
        envVarsMap.put("DATADIR", "/data/teamcity_server/datadir");
        envVarsMap.put("LOGSDIR", "/opt/teamcity/logs");
        envVarsMap.put("TEAMCITY_SERVER_OPTS", "-Dteamcity.startup.maintenance=false");

//        Map<String, Secret> secretVarsMap = new HashMap<>();
//        secretVarsMap.put("SECRET", Secret.from(secret));
//                "DB_PASSWORD", Secret.fromSecretsManager(dbSecret, "password"),  // Reference a specific JSON field, (requires platform version 1.4.0 or later for Fargate tasks)
//                "API_KEY", Secret.fromSecretsManagerVersion(secret, SecretVersionInfo.builder().versionId("12345").build(), "apiKey"),  // Reference a specific version of the secret by its version id or version stage (requires platform version 1.4.0 or later for Fargate tasks)
//                "PARAMETER", Secret.fromSsmParameter(parameter))

//        ContainerDefinition container = fargateTaskDefinition.addContainer("SetupContainer", ContainerDefinitionOptions.builder()
//                .image(ContainerImage.fromRegistry("postgres:14.3"))
//                .environment(envVarsMap)
////                .secrets(secretVarsMap)
//                .entryPoint(Arrays.asList("/bin/sh", "-c"))
//                .command(Collections.singletonList("apt install curl awscli jq -y"))
//                .build());
//        container.addMountPoints(dataDirMountPoint, logsMountPoint);


//        ScheduledFargateTask.Builder.create(this, "AAA")
//                .cluster(cluster)
//                .platformVersion(FargatePlatformVersion.LATEST)
//                .taskDefinition(fargateTaskDefinition);
//
//        EcsRunTask.Builder.create(this, "CustomTask")
//                .integrationPattern(IntegrationPattern.RUN_JOB)
//                .cluster(cluster)
//                .taskDefinition(fargateTaskDefinition)
//                .launchTarget(EcsFargateLaunchTarget.Builder.create().platformVersion(FargatePlatformVersion.LATEST).build())
//                .build();

        ApplicationLoadBalancedFargateService loadBalancedFargateService = ApplicationLoadBalancedFargateService.Builder.create(this, "Service")
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
                        .containerPort(8111)
                        .build())
                .build();
//
        loadBalancedFargateService.getTaskDefinition().addVolume(dataDirVolume);
        loadBalancedFargateService.getTaskDefinition().addVolume(logsVolume);
        fileSystem.getConnections().allowDefaultPortFrom(loadBalancedFargateService.getService().getConnections());

        fileSystem.grant(loadBalancedFargateService.getTaskDefinition().getTaskRole(), "elasticfilesystem:ClientMount", "elasticfilesystem:ClientWrite");


        LogGroup setupContainerLogGroup = LogGroup.Builder.create(this, "SetupLogGroup")
                .retention(RetentionDays.ONE_WEEK)
                .build();

        ContainerDefinition containerDefinition = loadBalancedFargateService.getService().getTaskDefinition().addContainer("Setup", ContainerDefinitionOptions.builder()
                        .image(ContainerImage.fromRegistry("postgres:14.3"))
                        .environment(envVarsMap)
                        .entryPoint(Arrays.asList("/bin/sh", "-c"))
                        .command(Collections.singletonList(
//                                "/bin/sh",
//                                "-c",
                                "apt-get update; apt-get install curl -y; mkdir -p /usr/local/lib/jdbc; curl https://jdbc.postgresql.org/download/postgresql-42.3.4.jar --output /usr/local/lib/jdbc/postgresql-42.3.4.jar; ls /usr/local/lib/jdbc; " + createDatabaseSh
                        ))
                        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(setupContainerLogGroup)
                                        .streamPrefix("setup")
                                .build()))
                        .build());
        containerDefinition.addMountPoints(dataDirMountPoint, logsMountPoint);

//        loadBalancedFargateService.getTaskDefinition().addContainer("AAA", ContainerDefinitionOptions.builder()
//                        .vo
//                .build());
        //add mount points to the TC container
        loadBalancedFargateService.getTaskDefinition().getDefaultContainer().addMountPoints(dataDirMountPoint, logsMountPoint);
        loadBalancedFargateService.getTaskDefinition().getDefaultContainer().addContainerDependencies(ContainerDependency.builder()
                .container(containerDefinition)
                .condition(ContainerDependencyCondition.COMPLETE)
                .build());
        System.out.println("TEST" + loadBalancedFargateService.getTaskDefinition().getDefaultContainer().getContainerName());

        System.out.println(loadBalancedFargateService.getTaskDefinition().getDefaultContainer().getLogDriverConfig().getLogDriver());
        System.out.println(loadBalancedFargateService.getTaskDefinition().getDefaultContainer().getLogDriverConfig().getOptions());


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
