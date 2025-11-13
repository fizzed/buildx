package com.fizzed.buildx.prepare;

import com.fizzed.buildx.Host;
import com.fizzed.buildx.HostExecute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrepareHostCopyMavenSettings implements HostExecute {
    static private final Logger log = LoggerFactory.getLogger(PrepareHostCopyMavenSettings.class);

    @Override
    public void execute(Host host) throws Exception {
        log.info("Copying ~/.m2/settings.xml -> .buildx-cache/.m2/settings.xml");

        // we want to supply containers with the hosts ~/.m2/settings.xml file for faster maven builds
        host.mkdir(".buildx-cache/.m2")
            .run();

        host.cp("~/.m2/settings.xml", ".buildx-cache/.m2/settings.xml")
            .run();
    }

}