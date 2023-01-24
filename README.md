[![official project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)


# TeamCity AWS CloudFormation template (Cloud Development Kit)

This project allows you to generate a TeamCity CloudFormation template for a quick setup in an AWS account.
It was created using the [AWS Cloud Development Kit (AWS CDK)](https://aws.amazon.com/cdk/) with Java.

# AWS Resources architecture
![AWS Resources architecture](./assets/images/AwsArchitecture.png)

# How to use
* Install the [CDK toolkit](https://docs.aws.amazon.com/cdk/v2/guide/cli.html).
* Run the `cdk synth` command to generate the CloudFormation template.
* Use the **cdk.out/CdkTeamcityTemplateStack.template.json** file when [creating a stack](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/resource-import-new-stack.html) in AWS CloudFormation.

## Configuration
The `cdk.json` tells the CDK CLI how to execute your app.

## Other useful commands

* `mvn package`     compiles and runs tests.
* `cdk ls`          lists all stacks in the app.
* `cdk synth`       emits the synthesized CloudFormation template.
* `cdk deploy`      deploys this stack to your default AWS account/region.
* `cdk diff`        compares deployed stack with current state.
* `cdk docs`        opens the CDK documentation.