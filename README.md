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

## License

Copyright (C) 2023+ Fizzed, Inc.

This work is licensed under the Apache License, Version 2.0. See LICENSE for details.