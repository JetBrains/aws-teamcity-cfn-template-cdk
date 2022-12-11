package jetbrains.teamcity.aws.template.vpc;

import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.List;

public class AwsTeamcityTemplateVpc {

    // VPC Configuration
    private static final String VPC_NAME = "TeamCityVpc";
    private static final int NAT_GTWs = 1;
    private static final int MAX_AZs = 2;
    //^


    // Subnets configuration
    private static final int PUBLIC_SUBNETS_CIDR_MASK = 24;
    private static final String PUBLIC_SUBNETS_NAME = "public-subnet";
    private static final int PRIVATE_SUBNETS_CIDR_MASK = 24;
    private static final String PRIVATE_SUBNETS_NAME = "private-subnet";
    //^

    public static Vpc buildVpc(@NotNull final Construct scope) {
        List<SubnetConfiguration> subnets = Arrays.asList(
                SubnetConfiguration.builder()
                        .cidrMask(PUBLIC_SUBNETS_CIDR_MASK)
                        .mapPublicIpOnLaunch(true)
                        .subnetType(SubnetType.PUBLIC)
                        .name(PUBLIC_SUBNETS_NAME)
                        .build(),
                SubnetConfiguration.builder()
                        .cidrMask(PRIVATE_SUBNETS_CIDR_MASK)
                        .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                        .name(PRIVATE_SUBNETS_NAME)
                        .build()
        );

        return Vpc.Builder.create(scope, VPC_NAME)
                .natGateways(NAT_GTWs)
                .enableDnsHostnames(true)
                .enableDnsSupport(true)
                .maxAzs(MAX_AZs)
                .subnetConfiguration(subnets)
                .build();
    }
}
