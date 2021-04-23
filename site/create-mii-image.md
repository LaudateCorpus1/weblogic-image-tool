# Create Files Image

The `filesimage` command helps build a WebLogic Docker image from a given base OS image for deploying to WebLogic Kubernetes Operator Model In Image domain source type. The required option for the command is marked. There are a number of optional parameters for the create feature.

```
Usage: imagetool filesimage [OPTIONS]
```

| Parameter | Definition | Default |
| --- | --- | --- |
| `--buildNetwork` | Networking mode for the RUN instructions during the image build.  See `--network` for Docker `build`.  |   |
| `--chown` | `userid:groupid` for JDK/Middleware installs and patches.  | `oracle:oracle` |
| `--docker` | Path to the Docker executable.  |  `docker` |
| `--dryRun` | Skip Docker build execution and print the Dockerfile to stdout.  |  |
| `--fromImage` | Docker image to use as a base image when creating a new image. | `ghcr.io/oracle/oraclelinux:7-slim`  |
| `--httpProxyUrl` | Proxy for the HTTP protocol. Example: `http://myproxy:80` or `http:user:passwd@myproxy:8080`  |   |
| `--httpsProxyUrl` | Proxy for the HTTPS protocol. Example: `https://myproxy:80` or `https:user:passwd@myproxy:8080`  |   |
| `--packageManager` | Override the default package manager for the base image's operating system. Supported values: `APK`, `APTGET`, `NONE`, `OS_DEFAULT`, `YUM`, `ZYPPER`  | `OS_DEFAULT`  |
| `--pull` | Always attempt to pull a newer version of base images during the build.  |   |
| `--tag` | (Required) Tag for the final build image. Example: `store/oracle/weblogic:12.2.1.3.0`  |   |
| `--wdtArchive` | Path to the WDT archive file used by the WDT model.  |   |
| `--wdtModel` | Path to the WDT model file that defines the domain to create.  |   |
| `--wdtVariables` | Path to the WDT variables file for use with the WDT model.  |   |
| `--wdtVersion` | WDT tool version to use.  |   |
| `--sourceFiles` | Comma separated list of path or files to copy to the mountPath.  |   |
| `--mountPath` | Files destination root folder path.  |   |

Note:  `--wdtModel` `--wdtVariables` `--wdtArchive` will be copied to a folder `models` under `--mountPath`. and WDT binaries will be unzipped to a folder `weblogic-deploy` under `--mountPath`.

#### Use an argument file

You can save all arguments passed for the Image Tool in a file, then use the file as a parameter.

For example, create a file called `build_args`:

```bash
create
--type wls
--version 12.2.1.3.0
--tag wls:122130
--user acmeuser@mycompany.com
--httpProxyUrl http://mycompany-proxy:80
--httpsProxyUrl http://mycompany-proxy:80
--passwordEnv MYPWD

```

Use it on the command line, as follows:

```bash
imagetool @/path/to/build_args
```

## Usage scenarios

**Note**: Use `--passwordEnv` or `--passwordFile` instead of `--password`.

The commands below assume that all the required JDK, WLS, or FMW (WebLogic infrastructure installers) have been downloaded
 to the cache directory. Use the [cache](cache.md) command to set it up.

- Create an image named `model-in-image:wlsv1` with the WDT resources.
  
  ```
  imagetool.sh cache addInstaller --type wdt --version latest /home/acme/wdt/weblogic-deploy.zip
  imagetool.sh create_mii_image --wdtModel /home/acme/wdt/models/model1.yaml --wdtVariables /home/acme/wdt/models/model1.10.properties --wdtArchive /Users/acme/wdt/models/archive1.zip   --tag model-in-image:wlsv1 --wdtVersion latest --wdtModelHome /u01/models
  ```

## Copyright
Copyright (c) 2019, 2021, Oracle and/or its affiliates.
