// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package com.oracle.weblogic.imagetool.cli.menu;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.weblogic.imagetool.logging.LoggingFacade;
import com.oracle.weblogic.imagetool.logging.LoggingFactory;
import com.oracle.weblogic.imagetool.util.AdditionalBuildCommands;
import com.oracle.weblogic.imagetool.util.Constants;
import com.oracle.weblogic.imagetool.util.DockerBuildCommand;
import com.oracle.weblogic.imagetool.util.DockerfileOptions;
import com.oracle.weblogic.imagetool.util.Utils;
import picocli.CommandLine.Option;
import picocli.CommandLine.Unmatched;


public class CommonOptionsBase {
    private static final LoggingFacade logger = LoggingFactory.getLogger(CommonOptionsBase.class);
    private static final String FILESFOLDER = "files";

    DockerfileOptions dockerfileOptions;
    private String tempDirectory = null;
    private String nonProxyHosts = null;

    private void handleChown() {
        if (osUserAndGroup.length != 2) {
            throw new IllegalArgumentException(Utils.getMessage("IMG-0027"));
        }

        Pattern p = Pattern.compile("^[a-z_]([a-z0-9_-]{0,31}|[a-z0-9_-]{0,30}\\$)$");
        Matcher usr = p.matcher(osUserAndGroup[0]);
        if (!usr.matches()) {
            throw new IllegalArgumentException(Utils.getMessage("IMG-0028", osUserAndGroup[0]));
        }
        Matcher grp = p.matcher(osUserAndGroup[1]);
        if (!grp.matches()) {
            throw new IllegalArgumentException(Utils.getMessage("IMG-0029", osUserAndGroup[1]));
        }

        dockerfileOptions.setUserId(osUserAndGroup[0]);
        dockerfileOptions.setGroupId(osUserAndGroup[1]);
    }

    private void handleAdditionalBuildCommands() throws IOException {
        if (additionalBuildCommandsPath != null) {
            if (!Files.isRegularFile(additionalBuildCommandsPath)) {
                throw new FileNotFoundException(Utils.getMessage("IMG-0030", additionalBuildCommandsPath));
            }

            AdditionalBuildCommands additionalBuildCommands = AdditionalBuildCommands.load(additionalBuildCommandsPath);
            dockerfileOptions.setAdditionalBuildCommands(additionalBuildCommands.getContents());
        }

        if (additionalBuildFiles != null) {
            Files.createDirectory(Paths.get(getTempDirectory(), FILESFOLDER));
            for (Path additionalFile : additionalBuildFiles) {
                if (!Files.isReadable(additionalFile)) {
                    throw new FileNotFoundException(Utils.getMessage("IMG-0030", additionalFile));
                }
                Path targetFile = Paths.get(getTempDirectory(), FILESFOLDER, additionalFile.getFileName().toString());
                logger.info("IMG-0043", additionalFile);
                if (Files.isDirectory(additionalFile)) {
                    Utils.copyLocalDirectory(additionalFile, targetFile, false);
                } else {
                    Utils.copyLocalFile(additionalFile, targetFile, false);
                }
            }
        }
    }

    void runDockerCommand(String dockerfile, DockerBuildCommand command) throws IOException, InterruptedException {
        logger.info("docker cmd = " + command.toString());

        if (dryRun) {
            System.out.println("########## BEGIN DOCKERFILE ##########");
            System.out.println(dockerfile);
            System.out.println("########## END DOCKERFILE ##########");
        } else {
            command.run(dockerLog);
        }
    }


    /**
     * Builds the options for docker build command.
     *
     * @return list of options
     */
    DockerBuildCommand getInitialBuildCmd(String contextFolder) {
        logger.entering();
        DockerBuildCommand cmdBuilder = new DockerBuildCommand(contextFolder);

        cmdBuilder.forceRm(!skipcleanup)
            .tag(imageTag)
            .network(buildNetwork)
            .pull(buildPull)
            .buildArg("http_proxy", httpProxyUrl)
            .buildArg("https_proxy", httpsProxyUrl)
            .buildArg("no_proxy", nonProxyHosts);

        if (dockerPath != null && Files.isExecutable(dockerPath)) {
            cmdBuilder.dockerPath(dockerPath.toAbsolutePath().toString());
        }
        logger.exiting();
        return cmdBuilder;
    }

    private void handleProxyUrls() throws IOException {
        httpProxyUrl = Utils.findProxyUrl(httpProxyUrl, Constants.HTTP);
        httpsProxyUrl = Utils.findProxyUrl(httpsProxyUrl, Constants.HTTPS);
        nonProxyHosts = Utils.findProxyUrl(nonProxyHosts, "none");
        Utils.setProxyIfRequired(httpProxyUrl, httpsProxyUrl, nonProxyHosts);
    }

    String getTempDirectory() throws IOException {
        if (tempDirectory == null) {
            Path tmpDir = Files.createTempDirectory(Paths.get(Utils.getBuildWorkingDir()), "wlsimgbuilder_temp");
            tempDirectory = tmpDir.toAbsolutePath().toString();
            logger.info("IMG-0003", tempDirectory);
        }
        return tempDirectory;
    }

    /**
     * Override the default behavior for generating a temp directory.
     * This method is used by UNIT tests ONLY.
     * @param value should be the value of a temp directory generated by the the UNIT test framework
     */
    void setTempDirectory(String value) {
        tempDirectory = value;
    }

    void init(String buildId) throws Exception {
        logger.entering(buildId);
        dockerfileOptions = new DockerfileOptions(buildId);
        logger.info("IMG-0016", buildId);

        handleProxyUrls();

        handleChown();
        handleAdditionalBuildCommands();

        logger.exiting();
    }


    /**
     * Set the docker options (dockerfile template bean) by extracting information from the fromImage.
     * @param fromImage image tag of the starting image
     * @param tmpDir    name of the temp directory to use for the build context
     * @throws IOException when a file operation fails.
     * @throws InterruptedException if an interrupt is received while trying to run a system command.
     */
    public void copyOptionsFromImage(String fromImage, String tmpDir) throws IOException, InterruptedException {

        if (fromImage != null && !fromImage.isEmpty()) {
            logger.finer("IMG-0002", fromImage);
            dockerfileOptions.setBaseImage(fromImage);

            Utils.copyResourceAsFile("/probe-env/test-create-env.sh",
                tmpDir + File.separator + "test-env.sh", true);

            Properties baseImageProperties = Utils.getBaseImageProperties(fromImage, tmpDir);

            if (baseImageProperties.getProperty("WLS_VERSION", null) != null) {
                throw new IllegalArgumentException(Utils.getMessage("IMG-0038", fromImage,
                    baseImageProperties.getProperty("ORACLE_HOME")));
            }

            String existingJavaHome = baseImageProperties.getProperty("JAVA_HOME", null);
            if (existingJavaHome != null) {
                dockerfileOptions.disableJavaInstall(existingJavaHome);
                logger.info("IMG-0000", existingJavaHome);
            }

            String pkgMgrProp = baseImageProperties.getProperty("PACKAGE_MANAGER", "YUM");

            PackageManagerType pkgMgr = PackageManagerType.valueOf(pkgMgrProp);
            logger.fine("fromImage package manager {0}", pkgMgr);
            if (packageManager != PackageManagerType.OS_DEFAULT && pkgMgr != packageManager) {
                logger.info("IMG-0079", pkgMgr, packageManager);
                pkgMgr = packageManager;
            }
            dockerfileOptions.setPackageInstaller(pkgMgr);
        } else if (packageManager == PackageManagerType.OS_DEFAULT) {
            // Default OS is Oracle Linux 7-slim, so default package manager is YUM
            dockerfileOptions.setPackageInstaller(PackageManagerType.YUM);
        } else {
            dockerfileOptions.setPackageInstaller(packageManager);
        }
    }


    @Option(
        names = {"--tag"},
        paramLabel = "TAG",
        required = true,
        description = "Tag for the final build image. Ex: store/oracle/weblogic:12.2.1.3.0"
    )
    String imageTag;

    @Option(
        names = {"--httpProxyUrl"},
        description = "proxy for http protocol. Ex: http://myproxy:80 or http://user:passwd@myproxy:8080"
    )
    private String httpProxyUrl;

    @Option(
        names = {"--httpsProxyUrl"},
        description = "proxy for https protocol. Ex: http://myproxy:80 or http://user:passwd@myproxy:8080"
    )
    private String httpsProxyUrl;

    @Option(
        names = {"--docker"},
        description = "path to docker executable. Default: ${DEFAULT-VALUE}",
        defaultValue = "docker"
    )
    private Path dockerPath;

    @Option(
        names = {"--dockerLog"},
        description = "file to log output from the docker build",
        hidden = true
    )
    private Path dockerLog;

    @Option(
        names = {"--skipcleanup"},
        description = "Do no delete Docker context folder, intermediate images, and failed build container."
    )
    boolean skipcleanup = false;

    @Option(
        names = {"--chown"},
        split = ":",
        description = "userid:groupid for JDK/Middleware installs and patches. Default: ${DEFAULT-VALUE}.",
        defaultValue = "oracle:oracle"
    )
    private String[] osUserAndGroup;

    @Option(
        names = {"--additionalBuildCommands"},
        description = "path to a file with additional build commands"
    )
    private Path additionalBuildCommandsPath;

    @Option(
        names = {"--additionalBuildFiles"},
        split = ",",
        description = "comma separated list of files that should be copied to the build context folder"
    )
    private List<Path> additionalBuildFiles;

    @Option(
        names = {"--dryRun"},
        description = "Skip Docker build execution and print Dockerfile to stdout"
    )
    boolean dryRun = false;

    @Option(
        names = {"--buildNetwork"},
        description = "Set the networking mode for the RUN instructions during build"
    )
    String buildNetwork;

    @Option(
        names = {"--pull"},
        description = "Always attempt to pull a newer version of base images during the build"
    )
    private boolean buildPull = false;

    @Option(
        names = {"--packageManager"},
        description = "Set the Linux package manager to use for installing OS packages. Default: ${DEFAULT-VALUE}"
    )
    PackageManagerType packageManager = PackageManagerType.OS_DEFAULT;

    @SuppressWarnings("unused")
    @Unmatched
    List<String> unmatchedOptions;
}
