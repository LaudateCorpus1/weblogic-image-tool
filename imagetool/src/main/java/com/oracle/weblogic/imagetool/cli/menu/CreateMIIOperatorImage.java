// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package com.oracle.weblogic.imagetool.cli.menu;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Callable;

import com.oracle.weblogic.imagetool.api.model.CachedFile;
import com.oracle.weblogic.imagetool.api.model.CommandResponse;
import com.oracle.weblogic.imagetool.installer.InstallerType;
import com.oracle.weblogic.imagetool.logging.LoggingFacade;
import com.oracle.weblogic.imagetool.logging.LoggingFactory;
import com.oracle.weblogic.imagetool.util.DockerBuildCommand;
import com.oracle.weblogic.imagetool.util.DockerfileOptions;
import com.oracle.weblogic.imagetool.util.Utils;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import static com.oracle.weblogic.imagetool.cachestore.CacheStoreFactory.cache;

@Command(
    name = "build",
    description = "Build Operator MII model image",
    requiredOptionMarker = '*',
    abbreviateSynopsis = true
)
public class CreateMIIOperatorImage extends CommonOptionsBase implements Callable<CommandResponse> {

    private static final LoggingFacade logger = LoggingFactory.getLogger(CreateMIIOperatorImage.class);
    private static final String FILESFOLDER = "files";

    @Override
    public CommandResponse call() throws Exception {
        logger.entering();
        Instant startTime = Instant.now();
        String tmpDir = null;
        String buildId = UUID.randomUUID().toString();

        try {
            init(buildId);

            tmpDir = getTempDirectory();
            copyOptionsFromImage(fromImage, tmpDir);

            DockerBuildCommand cmdBuilder = getInitialBuildCmd(tmpDir);
            // build wdt args if user passes --wdtModelPath
            handleWdtArgs(dockerfileOptions, cmdBuilder, tmpDir);

            if (packageManager == PackageManagerType.OS_DEFAULT) {
                // Default OS is Oracle Linux 7-slim, so default package manager is YUM
                dockerfileOptions.setPackageInstaller(PackageManagerType.YUM);
            } else {
                dockerfileOptions.setPackageInstaller(packageManager);
            }

            if (sourceFiles != null) {
                dockerfileOptions.setHasCopySourcePath();
                Files.createDirectory(Paths.get(getTempDirectory(), FILESFOLDER));
                for (Path sourceFile : sourceFiles) {
                    if (!Files.isReadable(sourceFile)) {
                        throw new FileNotFoundException(Utils.getMessage("IMG-0030", sourceFile));
                    }
                    Path targetFile = Paths.get(getTempDirectory(), FILESFOLDER, sourceFile.getFileName().toString());
                    logger.info("IMG-0043", sourceFile);
                    if (Files.isDirectory(sourceFile)) {
                        Utils.copyLocalDirectory(sourceFile, targetFile, false);
                    } else {
                        Utils.copyLocalFile(sourceFile, targetFile, false);
                    }
                }
            }


            // Create Dockerfile

            String dockerfile = Utils.writeDockerfile(tmpDir + File.separator + "Dockerfile",
                "CreateMIIOperator_Image.mustache", dockerfileOptions, dryRun);

            runDockerCommand(dockerfile, cmdBuilder);
        } catch (Exception ex) {
            logger.fine("**ERROR**", ex);
            return new CommandResponse(-1, ex.getMessage());
        } finally {
            if (!skipcleanup) {
                Utils.deleteFilesRecursively(tmpDir);
                Utils.removeIntermediateDockerImages(buildId);
            }
        }
        Instant endTime = Instant.now();
        logger.exiting();
        if (dryRun) {
            return new CommandResponse(0, "IMG-0054");
        } else {
            return new CommandResponse(0, "IMG-0053",
                Duration.between(startTime, endTime).getSeconds(), imageTag);
        }
    }

    /**
     * Checks whether the user requested a domain to be created with WDT.
     * If so,  creates required file links to pass the model, archive, variables file to build process.
     *
     * @param tmpDir the tmp directory which is passed to docker as the build context directory
     * @throws IOException in case of error
     */
    void handleWdtArgs(DockerfileOptions dockerfileOptions, DockerBuildCommand cmdBuilder, String tmpDir)
        throws IOException {

        logger.entering(tmpDir);

        dockerfileOptions.setWdtEnabled()
            .setMiiMountPath("/" + mountPath)
            .setWdtHome("/" + mountPath)
            .setWdtModelHome("/" + mountPath + File.separator + "models");

        if (wdtModelPath != null) {
            List<String> modelList = addWdtFilesAsList(wdtModelPath, "model", tmpDir);
            dockerfileOptions.setWdtModels(modelList);
        }

        if (wdtArchivePath != null) {
            List<String> archiveList = addWdtFilesAsList(wdtArchivePath, "archive", tmpDir);
            dockerfileOptions.setWdtArchives(archiveList);
        }

        if (wdtVariablesPath != null && Files.isRegularFile(wdtVariablesPath)) {
            String wdtVariableFilename = wdtVariablesPath.getFileName().toString();
            Files.copy(wdtVariablesPath, Paths.get(tmpDir, wdtVariableFilename));
            //Until WDT supports multiple variable files, take single file argument from CLI and convert to list
            dockerfileOptions.setWdtVariables(Collections.singletonList(wdtVariableFilename));
        }

        CachedFile wdtInstaller = new CachedFile(InstallerType.WDT, wdtVersion);
        Path wdtfile = wdtInstaller.copyFile(cache(), tmpDir);
        dockerfileOptions.setWdtInstallerFilename(wdtfile.getFileName().toString());

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

            Properties baseImageProperties = Utils.getBaseImageProperties(fromImage, tmpDir);
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


    private List<String> addWdtFilesAsList(Path fileArg, String type, String tmpDir) throws IOException {
        String[] listOfFiles = fileArg.toString().split(",");
        List<String> fileList = new ArrayList<>();

        for (String individualFile : listOfFiles) {
            Path individualPath = Paths.get(individualFile);
            if (Files.isRegularFile(individualPath)) {
                String modelFilename = individualPath.getFileName().toString();
                Files.copy(individualPath, Paths.get(tmpDir, modelFilename));
                fileList.add(modelFilename);
            } else {
                String errMsg = String.format("WDT %s file %s not found ", type, individualFile);
                throw new IOException(errMsg);
            }
        }
        return fileList;
    }

    @CommandLine.Option(
        names = {"--fromImage"},
        description = "Docker image to use as base image."
    )
    private String fromImage;

    @CommandLine.Option(
        names = {"--wdtModel"},
        description = "path to the WDT model file that defines the Domain to create"
    )
    private Path wdtModelPath;

    @CommandLine.Option(
        names = {"--wdtArchive"},
        description = "path to the WDT archive file used by the WDT model"
    )
    private Path wdtArchivePath;

    @CommandLine.Option(
        names = {"--wdtVariables"},
        description = "path to the WDT variables file for use with the WDT model"
    )
    private Path wdtVariablesPath;

    @CommandLine.Option(
        names = {"--wdtVersion"},
        description = "WDT tool version to use",
        defaultValue = "latest"
    )
    private String wdtVersion;

    @CommandLine.Option(
        names = {"--mountPath"},
        description = "The root directory where the WDT binaries and models will be create. WDT binaries will be in "
            + " subdirectory weblogic-deploy and WDT models will be under models",
        defaultValue = "common"
    )
    private String mountPath;

    @CommandLine.Option(
        names = {"--copyFilesOnly"},
        description = "Copy files only",
        defaultValue = "true"
    )
    private String copyFilesOnly;

    @CommandLine.Option(
        names = {"--sourceFiles"},
        split = ",",
        description = "Comma separated path or files"
    )
    private List<Path> sourceFiles;


}
