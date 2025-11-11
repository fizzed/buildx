package com.fizzed.buildx.internal;

import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.system.Exec;
import com.fizzed.buildx.Project;
import com.fizzed.buildx.Target;
import org.slf4j.Logger;

import java.io.PrintStream;

public class ProjectImpl implements Project {
    private final Logger log = Contexts.logger();

    private final HostImpl host;
    private final Target target;

    public ProjectImpl(HostImpl host, Target target) {
        this.host = host;
        this.target = target;
    }

    @Override
    public PrintStream out() {
        return this.host.out();
    }

    /*@Override
    public Exec rsync(String sourcePath, String destPath) {
        return this.host.rsync(sourcePath, destPath);
    }*/

    /**
     * Executes a command ON the logical project such as in a container.
     */
    @Override
    public Exec exec(String exeOrNameOfExe, Object... arguments) {
        // container?
        if (this.target.getContainerImage() != null) {
            final String projectPath;
            if (this.host.getSshSession() != null) {
                projectPath = this.host.getRemoteDir();
            } else {
                projectPath = this.host.getAbsoluteDir().toString();
            }

            // LOCAL + Container (we need to map the local path! for docker)
            // adding ":z" fixes podman to mount as the user
            // https://stackoverflow.com/questions/75817076/no-matter-what-i-do-podman-is-mounting-volumes-as-root
            final Exec exec = this.host.exec(this.host.getInfo().resolveContainerExe(), "run",
                // the working dir becomes the home dir
                "-v", projectPath + "/.buildx-cache" + ":/buildx-cache:z",
                "-w", "/buildx-cache",
                "-e", "HOME=/buildx-cache",
                "-v", projectPath + ":/project:z",
                "--userns=keep-id");

            // add other volumes
            /*for (Map.Entry<String,String> entry : this.containerVolumes.entrySet()) {
                String sourcePath = entry.getKey();
                String containerPath = entry.getValue();
                // do we need to fix the path at all?
                if (sourcePath.startsWith("~/")) {
                    sourcePath = this.hostInfo.getHomeDir() + sourcePath.substring(1);
                }
                if (containerPath.startsWith("~/")) {
                    containerPath = "/buildx-cache" + containerPath.substring(1);
                }

                exec.args("-v", sourcePath + ":" + containerPath + ":z");
            }*/

            exec.args(this.target.getContainerImage(), "/project/.buildx/container-exec.sh", "/project", exeOrNameOfExe);

            // add other arguments now
            return exec.args(arguments);


            /*return this.hostExec(this.containerExe, "run",
                    // the working dir becomes the home dir
                    "-v", projectPath + "/.buildx-cache" + ":/buildx-cache:z",
                    "-w", "/buildx-cache",
                    "-e", "HOME=/buildx-cache",
                    "-v", projectPath + ":/project:z",
                    "--userns=keep-id", this.target.getContainerImage(),
                    "/project/.buildx/container-exec.sh", "/project",
                    exeOrNameOfExe)
                .args(arguments);*/
        } else {
            return this.host.exec(exeOrNameOfExe)
                .args(arguments);
        }
    }

    /*public void prepareForContainers() {
        // TODO: allow container builder to control what we're going to setup for caching???
        log.info("Creating .buildx-cache on container host...");

        for (String dir : asList(".buildx-cache")) {
            if (this.hostInfo.getOs() == OperatingSystem.WINDOWS) {
                dir = dir.replace("/", this.hostInfo.getFileSeparator());

                this.hostExec("cmd", "/C", "md \"" + dir + "\"")
                    .exitValues(0, 1)       // if dir already exists it errors out with 1
                    .run();
            } else {
                this.hostExec("mkdir", "-p", dir)
                    .run();
            }
        }
    }*/

}