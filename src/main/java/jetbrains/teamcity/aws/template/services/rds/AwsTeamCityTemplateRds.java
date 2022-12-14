package jetbrains.teamcity.aws.template.services.rds;

import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.SecretValue;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.rds.*;
import software.constructs.Construct;

public class AwsTeamCityTemplateRds {

    public static final String DATABASE_USERNAME = "teamcity";

    public static final String DATABASE_NAME = "teamcity";


    private final DatabaseInstance myDatabaseInstance;
    public AwsTeamCityTemplateRds(@NotNull final Construct scope,
                                  @NotNull final Vpc vpc,
                                  @NotNull final ApplicationLoadBalancedFargateService loadBalancedFargateService,
                                  @NotNull final SecretValue masterDbPassword) {
        myDatabaseInstance = DatabaseInstance.Builder.create(scope, "TeamCityDatabase")
                .engine(DatabaseInstanceEngine.postgres(PostgresInstanceEngineProps.builder().version(PostgresEngineVersion.VER_14_3).build()))
                .instanceIdentifier("TeamcityTemplateDatabase")
                .databaseName(AwsTeamCityTemplateRds.DATABASE_NAME)
                .allocatedStorage(30)
                .maxAllocatedStorage(100)
                .autoMinorVersionUpgrade(false)
                .backupRetention(Duration.days(0))
                .multiAz(false)
                .instanceType(InstanceType.of(InstanceClass.T4G, InstanceSize.SMALL))
                .vpc(vpc)
                .vpcSubnets(SubnetSelection.builder()
                        .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                        .build())
                .credentials(Credentials.fromPassword(AwsTeamCityTemplateRds.DATABASE_USERNAME, masterDbPassword))
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // Allow connections from the ECS service with setup task
        myDatabaseInstance.getConnections().allowDefaultPortFrom(loadBalancedFargateService.getService().getConnections());
    }

    public DatabaseInstance getDatabaseInstance() {
        return myDatabaseInstance;
    }
}
