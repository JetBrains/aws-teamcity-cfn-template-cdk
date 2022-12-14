package jetbrains.teamcity.aws.template.services.efs;

import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.AuthorizationConfig;
import software.amazon.awscdk.services.ecs.EfsVolumeConfiguration;
import software.amazon.awscdk.services.ecs.MountPoint;
import software.amazon.awscdk.services.ecs.Volume;
import software.amazon.awscdk.services.efs.*;
import software.constructs.Construct;

public class AwsTeamCityTemplateEfs {

    private final FileSystem myFileSystem;
    private final Volume myDataDirVolume;
    private final Volume myLogsDirVolume;
    private final MountPoint myDataDirMountPoint;
    private final MountPoint myLogsDirMountPoint;

    public AwsTeamCityTemplateEfs(@NotNull final Construct scope, @NotNull final Vpc vpc) {

        myFileSystem = FileSystem.Builder.create(scope, "MyEfsFileSystem")
                .vpc(vpc)
                .lifecyclePolicy(LifecyclePolicy.AFTER_14_DAYS) // files are not transitioned to infrequent access (IA) storage by default
                .performanceMode(PerformanceMode.GENERAL_PURPOSE) // default
                .throughputMode(ThroughputMode.BURSTING)
                .outOfInfrequentAccessPolicy(OutOfInfrequentAccessPolicy.AFTER_1_ACCESS)
                .removalPolicy(RemovalPolicy.DESTROY) // otherwise stateful resources will not be deleted when Clfn stack is deleted
                .build();

        AccessPoint dataDirAccessPoint = AccessPoint.Builder.create(scope, "TeamCity data")
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
                .fileSystem(myFileSystem)
                .build();
        dataDirAccessPoint.getNode().addDependency(myFileSystem);

        AccessPoint logsDirAccessPoint = AccessPoint.Builder.create(scope, "TeamCity logs")
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
                .fileSystem(myFileSystem)
                .build();
        logsDirAccessPoint.getNode().addDependency(myFileSystem);


        myDataDirVolume = Volume.builder()
                .name("Data")
                .efsVolumeConfiguration(EfsVolumeConfiguration.builder()
                        .fileSystemId(myFileSystem.getFileSystemId())
                        .transitEncryption("ENABLED")
                        .authorizationConfig(AuthorizationConfig.builder()
                                .accessPointId(dataDirAccessPoint.getAccessPointId())
                                .iam("ENABLED")
                                .build())
                        .build())
                .build();
        myLogsDirVolume = Volume.builder()
                .name("Logs")
                .efsVolumeConfiguration(EfsVolumeConfiguration.builder()
                        .fileSystemId(myFileSystem.getFileSystemId())
                        .transitEncryption("ENABLED")
                        .authorizationConfig(AuthorizationConfig.builder()
                                .accessPointId(logsDirAccessPoint.getAccessPointId())
                                .iam("ENABLED")
                                .build())
                        .build())
                .build();


        myDataDirMountPoint = MountPoint.builder()
                .containerPath("/data/teamcity_server/datadir")
                .readOnly(false)
                .sourceVolume(myDataDirVolume.getName())
                .build();
        myLogsDirMountPoint = MountPoint.builder()
                .containerPath("/opt/teamcity/logs")
                .readOnly(false)
                .sourceVolume(myLogsDirVolume.getName())
                .build();
    }

    public FileSystem getEfs() {
        return myFileSystem;
    }

    public Volume getDataDirVolume() {
        return myDataDirVolume;
    }

    public Volume getLogsDirVolume() {
        return myLogsDirVolume;
    }

    public MountPoint getDataDirMountPoint() {
        return myDataDirMountPoint;
    }

    public MountPoint getLogsDirMountPoint() {
        return myLogsDirMountPoint;
    }
}
