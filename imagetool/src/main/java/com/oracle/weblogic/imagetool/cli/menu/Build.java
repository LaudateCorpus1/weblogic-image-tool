// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package com.oracle.weblogic.imagetool.cli.menu;

import java.util.concurrent.Callable;

import com.oracle.weblogic.imagetool.api.model.CommandResponse;
import picocli.CommandLine;

@CommandLine.Command(
    name = "build",
    subcommands = {CreateMIIOperatorImage.class},
    description = "Build WebLogic docker image",
    requiredOptionMarker = '*',
    abbreviateSynopsis = true
)
public class Build implements Callable<CommandResponse> {
    @Override
    public CommandResponse call() throws Exception {
        return new CommandResponse(0, "IMG-0054");
    }
}
