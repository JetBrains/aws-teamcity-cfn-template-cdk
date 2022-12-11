package jetbrains.teamcity.aws.template;

import software.amazon.awscdk.App;
import software.amazon.awscdk.DefaultStackSynthesizer;
import software.amazon.awscdk.DefaultStackSynthesizerProps;
import software.amazon.awscdk.StackProps;

public class AwsTeamcityCfnTemplateCdkApp {
    public static void main(final String[] args) {
        App app = new App();

        new AwsTeamcityCfnTemplateCdkStack(app, "AwsTeamcityCfnTemplateCdkStack", StackProps.builder()
                .synthesizer(new DefaultStackSynthesizer(new DefaultStackSynthesizerProps() {
                    @Override
                    public Boolean getGenerateBootstrapVersionRule() {
                        return false;
                    }
                }))
                .build());
        app.synth();
    }
}

