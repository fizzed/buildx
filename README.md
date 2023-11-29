# Buildx by Fizzed

[![Maven Central](https://img.shields.io/maven-central/v/com.fizzed/buildx?color=blue&style=flat-square)](https://mvnrepository.com/artifact/com.fizzed/blaze-buildx)

## Overview

Blaze plugin for building projects across machines, hosts, and containers.  This repository also helps build and publish
docker containers for cross-building Java projects that require native code, as well as images across various architectures
and JDK versions to help test "minimum compatible" scenarios.

Buildx lets you execute arbitrary tasks you define locally, locally in a docker container, remotely via ssh, as well
as remotely via ssh in a docker container.

Buildx handles mounting/rsyncing your project repository before executing "actions" on it.  Those actions are entirely up to you
to define.  You can then rsync anything back as part of your action.  Whether you're running your tasks locally, in a docker
container, or remotely, the project environment is all setup for you to abstract away the complexity of the various
environments.

## Sponsorship & Support

![](https://cdn.fizzed.com/github/fizzed-logo-100.png)

Project by [Fizzed, Inc.](http://fizzed.com) (Follow on Twitter: [@fizzed_inc](http://twitter.com/fizzed_inc))

**Developing and maintaining opensource projects requires significant time.** If you find this project useful or need
commercial support, we'd love to chat. Drop us an email at [ping@fizzed.com](mailto:ping@fizzed.com)

Project sponsors may include the following benefits:

- Priority support (outside of Github)
- Feature development & roadmap
- Priority bug fixes
- Privately hosted continuous integration tests for their unique edge or use cases

## Usage

You'll need to use a [Blaze](https://github.com/fizzed/blaze) project and leverage this dependency to take advantage.

We haven't had time to document this project as well as we'd like, but here are 3 examples that should help get you started.
These examples help build & test Java projects that have a native-compiled library across Windows, MacOS, Linux, BSDs,
across various architectures:

https://github.com/fizzed/shmemj/blob/master/.blaze/blaze.java
https://github.com/fizzed/tokyocabinet/blob/master/setup/blaze.java
https://github.com/fizzed/tkrzw/blob/master/setup/blaze.java

## Containers for Java

These containers contain a single JDK version that is set as the default, along with Maven 3.9.5, and Blaze build tools.

| Container                            | Architecture | JDK    |
|--------------------------------------|--------------|--------|
| fizzed/buildx:arm64-alpine3.11-jdk11 | arm64        | JDK 11 |
| fizzed/buildx:arm64-ubuntu16-jdk11   | arm64        | JDK 11 |
| fizzed/buildx:arm64-ubuntu18-jdk11   | arm64        | JDK 11 |
| fizzed/buildx:arm64-ubuntu20-jdk11   | arm64        | JDK 11 |
| fizzed/buildx:armel-debian11-jdk11   | armel        | JDK 11 |
| fizzed/buildx:armhf-ubuntu16-jdk11   | armhf        | JDK 11 |
| fizzed/buildx:armhf-ubuntu18-jdk11   | armhf        | JDK 11 |
| fizzed/buildx:riscv64-ubuntu20-jdk21 | riscv64      | JDK 21 |
| fizzed/buildx:x32-ubuntu16-jdk11     | x32          | JDK 11 |
| fizzed/buildx:x32-ubuntu18-jdk11     | x32          | JDK 11 |
| fizzed/buildx:x64-ubuntu22-jdk21     | x64          | JDK 21 |
| fizzed/buildx:x64-ubuntu22-jdk17     | x64          | JDK 17 |
| fizzed/buildx:x64-alpine3.11-jdk11   | x64          | JDK 11 |
| fizzed/buildx:x64-ubuntu16-jdk11     | x64          | JDK 11 |
| fizzed/buildx:x64-ubuntu18-jdk11     | x64          | JDK 11 |
| fizzed/buildx:x64-ubuntu20-jdk11     | x64          | JDK 11 |
| fizzed/buildx:x64-ubuntu22-jdk11     | x64          | JDK 11 |
| fizzed/buildx:x64-ubuntu22-jdk8      | x64          | JDK 8  |

These containers are useful from cross building native code. They have latest versions of cmake, gcc, g++, rust, as 
well as JDK 11.

| Container                                                | Description                                           |
|----------------------------------------------------------|-------------------------------------------------------|
| fizzed/buildx:x64-ubuntu16-jdk11-buildx-linux-x64        | Cross compiling to linux-x64 from ubuntu16 x64        |
| fizzed/buildx:x64-ubuntu16-jdk11-buildx-linux-x32        | Cross compiling to linux-x32 from ubuntu16 x64        |
| fizzed/buildx:x64-ubuntu16-jdk11-buildx-linux_musl-x64   | Cross compiling to linux_musl-x64 from ubuntu16 x64   |
| fizzed/buildx:x64-ubuntu16-jdk11-buildx-linux_musl-arm64 | Cross compiling to linux_musl-arm64 from ubuntu16 x64 |
| fizzed/buildx:x64-ubuntu16-jdk11-buildx-linux-arm64      | Cross compiling to linux-arm64 from ubuntu16 x64      |
| fizzed/buildx:x64-ubuntu16-jdk11-buildx-linux-armhf      | Cross compiling to linux-armhf from ubuntu16 x64      |
| fizzed/buildx:x64-ubuntu16-jdk11-buildx-linux-armel      | Cross compiling to linux-armel from ubuntu16 x64      |
| fizzed/buildx:x64-ubuntu18-jdk11-buildx-linux-x64        | Cross compiling to linux-x64 from ubuntu18 x64        |
| fizzed/buildx:x64-ubuntu18-jdk11-buildx-linux-x32        | Cross compiling to linux-x32 from ubuntu18 x64        |
| fizzed/buildx:x64-ubuntu18-jdk11-buildx-linux_musl-x64   | Cross compiling to linux_musl-x64 from ubuntu18 x64   |
| fizzed/buildx:x64-ubuntu18-jdk11-buildx-linux_musl-arm64 | Cross compiling to linux_musl-arm64 from ubuntu18 x64 |
| fizzed/buildx:x64-ubuntu18-jdk11-buildx-linux-arm64      | Cross compiling to linux-arm64 from ubuntu18 x64      |
| fizzed/buildx:x64-ubuntu18-jdk11-buildx-linux-armhf      | Cross compiling to linux-armhf from ubuntu18 x64      |
| fizzed/buildx:x64-ubuntu18-jdk11-buildx-linux-armel      | Cross compiling to linux-armel from ubuntu18 x64      |
| fizzed/buildx:x64-ubuntu18-jdk11-buildx-linux-riscv64    | Cross compiling to linux-riscv64 from ubuntu18 x64    |

## Linux Compatibility

When you compile software on Linux, the libraries it depends on are important for whether it will run on future versions
of an operating system. Glibc is an important base library.  Here is a compatibility matrix for important distros:

| Operating System       | GLIBC      | Released |
|------------------------|------------|----------|
| Debian 12 (Bookworm)   | glibc 2.36 | 2023     |
| Debian 11 (Bullseye)   | glibc 2.31 | 2021     |
| Debian 10 (Buster)     | glibc 2.28 | 2019     |
| Debian 9 (Stretch)     | glibc 2.24 | 2017     |
| Debian 8 (Jessie)      | glibc 2.19 | 2015     |
| Debian 7 (Wheezy)      | glibc 2.13 | 2013     |
| Debian 6 (Squeeze)     | glibc 2.11 | 2011     |
| ---------------------  | ---------- | ----     |
| Ubuntu 22.04 (Jammy)   | glibc 2.35 | 2022     |
| Ubuntu 20.04 (Focal)   | glibc 2.31 | 2020     |
| Ubuntu 18.04 (Bionic)  | glibc 2.27 | 2018     |
| Ubuntu 16.04 (Xenial)  | glibc 2.23 | 2016     |
| Ubuntu 14.04 (Trusty)  | glibc 2.19 | 2014     |
| Ubuntu 12.04 (Precise) | glibc 2.15 | 2012     |
| Ubuntu 10.04 (Lucid)   | glibc 2.11 | 2010     |
| Ubuntu 8.04 (Hardy)    | glibc 2.6  | 2008     |
| Ubuntu 6.06 (Dapper)   | glibc 2.3  | 2006     |

## Multiple Architecture Containers

You can use an Ubuntu x86_64 host to test a wide variety of hardware architectures and operating systems.
Install QEMU and various emulators: https://www.stereolabs.com/docs/docker/building-arm-container-on-x86/

Option 1 is to use the multiarch method (which only works on an x86_64 host):

    sudo apt-get install qemu binfmt-support qemu-user-static
    docker run --rm --privileged multiarch/qemu-user-static --reset -p yes

Option 2 is to use the https://github.com/dbhi/qus method which can work on x86_64 and other arches:

    docker run --rm --privileged aptman/qus -s -- -p 

To unregister associations with either option above

    docker run --rm --privileged aptman/qus -- -r

This will now register docker to be able to detect and run various architectures automatically. You can now try it out:

    docker run --rm -t arm64v8/ubuntu dpkg --print-architecture       #arm64
    docker run --rm -t arm32v7/debian dpkg --print-architecture       #armhf
    docker run --rm -t arm32v5/debian dpkg --print-architecture       #armel
    docker run --rm -t riscv64/ubuntu dpkg --print-architecture       #riscv64
    docker run --rm -t i386/ubuntu dpkg --print-architecture          #i386

If you'd like to try various Java system properties to see what they'd look like:

    docker run --rm -it riscv64/ubuntu
    apt update
    apt install openjdk-11-jdk-headless
    jshell
    System.getProperties().forEach((k, v) -> { System.out.printf("%s: %s\n", k, v); })

## Build Environments

All machines will need to be accessible via SSH and include just a handful of tools. This will work across operating
systems, including Windows.

### Testing SSH Environment Variables

Blaze SSH will execute commands outside of a normal "shell" environment. Your environment variables that are present
may not be exactly what you'd expect or the same as you SSH'ing into the box.  To test this try:

     ssh YOUR-HOST-HERE env

If you run a shell script once you ssh into your host, you can add the "-l" flag to the top of your shell script, which
will request a "login" to be run, which may set the environment variables you expect. To test this:

     ssh YOUR-HOST-HERE sh -li -c "env"

That will run the environment command on the remote machine, but using an SSH exec, instead of it being done by 
requesting a shell.

### Windows

 - Setup a user account with no SPACE character in its name. Instead of "Henry Ford" something like "builder"
 - Enable the optional windows feature for "Openssh Server". This will only be available by default for Windows 10+
 - Rsync is critical. One way of adding it is to install Cygwin for 64-bit windows (cygwin.com)
 - When prompted for a package to install, search for rsync (and any others you'd like to add)
 - If you installed cygwin to C:\cygwin64 then add C:\cygwin64\bin to your system PATH environment variable
 - Install JDKs
 - Install Maven (or your preferred java project build tool)
 - Setup password-less ssh. Be VERY CAREFUL when adding your public key to your ~/.ssh/authorized_keys file
   - If you're an administrator, the default sshd_config config uses a global authorized keys file.
   - Open up C:\ProgramData\ssh\sshd_config as an adminstrator
   - If you comment that out and restart the ssh server, you can now create ~/.ssh/authorized_keys manually.
   - Also, allow UserPermitEnvironment to yes
   - Make sure to do this ON the box (and not via ssh) so that your permissions are correct
   - You may also want to make powershell your default shell when logging in https://learn.microsoft.com/en-us/windows-server/administration/openssh/openssh_server_configuration
   - Run "net stop sshd" and "net start sshd" to restart the daemon
   - NOTE: if passwordless ssh isn't working, the culprit is mostly likely that your authorized_keys file has bad permissions and windows will QUIETLY ignore it

## License

Copyright (C) 2023+ Fizzed, Inc.

This work is licensed under the Apache License, Version 2.0. See LICENSE for details.