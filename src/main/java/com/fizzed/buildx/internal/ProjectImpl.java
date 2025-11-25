package com.fizzed.buildx.internal;

import com.fizzed.blaze.Contexts;
import com.fizzed.blaze.core.Action;
import com.fizzed.blaze.system.Exec;
import com.fizzed.buildx.Project;
import com.fizzed.buildx.SkipException;
import com.fizzed.buildx.Target;
import org.slf4j.Logger;

public class ProjectImpl implements Project {
    private final Logger log = Contexts.logger();

    private final HostImpl host;
    private final ContainerImpl container;
    private final Target target;

    public ProjectImpl(HostImpl host, ContainerImpl container, Target target) {
        this.host = host;
        this.container = container;
        this.target = target;
    }

    @Override
    public void skip(String reason) throws SkipException {
        throw new SkipException(reason);
    }

    @Override
    public Action<?,?> rsync(String sourcePath, String destPath) {
        return this.host.rsync(sourcePath, destPath);
    }

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
                "--rm",     // make sure container deletes itself after it finishes
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
        } else {
            return this.host.exec(exeOrNameOfExe)
                .args(arguments);
        }
    }

}