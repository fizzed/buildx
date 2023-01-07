# Blaze Buildx by Fizzed

[![Maven Central](https://img.shields.io/maven-central/v/com.fizzed/blaze-buildx?style=flat-square)](https://mvnrepository.com/artifact/com.fizzed/blaze-buildx)

[Fizzed, Inc.](http://fizzed.com) (Follow on Twitter: [@fizzed_inc](http://twitter.com/fizzed_inc))

## Overview

Blaze plugin for building projects across machines, hosts, and containers.

## Usage

You'll need to use a [Blaze](https://github.com/fizzed/blaze) project and leverage this dependency to take advantage.
For a real world use, please check out https://github.com/fizzed/tokyocabinet/blob/master/setup/blaze.java

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