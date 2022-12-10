package com.myorg;

import software.constructs.Construct;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;

public class AwsTeamcityCfnTemplateCdkStack extends Stack {
    public AwsTeamcityCfnTemplateCdkStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public AwsTeamcityCfnTemplateCdkStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // The code that defines your stack goes here

        // example resource
        // final Queue queue = Queue.Builder.create(this, "AwsTeamcityCfnTemplateCdkQueue")
        //         .visibilityTimeout(Duration.seconds(300))
        //         .build();
    }
}
