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

package jetbrains.teamcity.aws.template.services.rds;

import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.SecretValue;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.rds.*;
import software.constructs.Construct;

import java.util.UUID;

public class AwsTeamCityTemplateRds {

    public static final String DATABASE_USERNAME = "teamcity";

    public static final String DATABASE_NAME = "teamcity";

    public static final String DATABASE_INSTANCE_IDENTIFIER = "TeamcityTemplateDatabase-" + UUID.randomUUID();

    public static final String DATABASE_MASTER_PASSWORD = UUID.randomUUID().toString();

    private final DatabaseInstance myDatabaseInstance;
    public AwsTeamCityTemplateRds(@NotNull final Construct scope,
                                  @NotNull final Vpc vpc,
                                  @NotNull final ApplicationLoadBalancedFargateService loadBalancedFargateService) {
        myDatabaseInstance = DatabaseInstance.Builder.create(scope, "TeamCityDatabase")
                .engine(DatabaseInstanceEngine.postgres(PostgresInstanceEngineProps.builder().version(PostgresEngineVersion.VER_16_2).build()))
                .instanceIdentifier(DATABASE_INSTANCE_IDENTIFIER)
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
                .credentials(Credentials.fromPassword(AwsTeamCityTemplateRds.DATABASE_USERNAME, new SecretValue(DATABASE_MASTER_PASSWORD)))
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // Allow connections from the ECS service with setup task
        myDatabaseInstance.getConnections().allowDefaultPortFrom(loadBalancedFargateService.getService().getConnections());
    }

    public DatabaseInstance getDatabaseInstance() {
        return myDatabaseInstance;
    }
}
