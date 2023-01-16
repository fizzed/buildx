#!/bin/sh -l
set -e

# x64, ubuntu 16.04, jdk11
docker build -f setup/Dockerfile.linux --progress=plain \
  --build-arg FROM_IMAGE=amd64/ubuntu:16.04 \
  --build-arg JAVA_VERSION=11 \
  -t fizzed/buildx:amd64-ubuntu16-jdk11 setup

docker push fizzed/buildx:amd64-ubuntu16-jdk11

# x64, ubuntu 18.04, jdk11 (we need this for riscv64 cross build)
docker build -f setup/Dockerfile.linux --progress=plain \
  --build-arg FROM_IMAGE=amd64/ubuntu:18.04 \
  --build-arg JAVA_VERSION=11 \
  -t fizzed/buildx:amd64-ubuntu18-jdk11 setup

docker push fizzed/buildx:amd64-ubuntu18-jdk11

# aarch64, ubuntu 16.04, jdk11
docker build -f setup/Dockerfile.linux --progress=plain \
  --build-arg FROM_IMAGE=arm64v8/ubuntu:16.04 \
  --build-arg JAVA_VERSION=11 \
  -t fizzed/buildx:arm64v8-ubuntu16-jdk11 setup

docker push fizzed/buildx:arm64v8-ubuntu16-jdk11

# armhf, ubuntu 16.04, jdk11
docker build -f setup/Dockerfile.linux --progress=plain \
  --build-arg FROM_IMAGE=arm32v7/ubuntu:16.04 \
  --build-arg JAVA_VERSION=11 \
  -t fizzed/buildx:arm32v7-ubuntu16-jdk11 setup

docker push fizzed/buildx:arm32v7-ubuntu16-jdk11

# armel, debian 11, jdk11
docker build -f setup/Dockerfile.linux --progress=plain \
  --build-arg FROM_IMAGE=arm32v5/debian:11 \
  --build-arg JAVA_VERSION=11 \
  -t fizzed/buildx:arm32v5-debian11-jdk11 setup

docker push fizzed/buildx:arm32v5-debian11-jdk11

# riscv64, ubuntu 20.04, jdk19
docker build -f setup/Dockerfile.linux --progress=plain \
  --build-arg FROM_IMAGE=riscv64/ubuntu:20.04 \
  --build-arg JAVA_VERSION=19 \
  -t fizzed/buildx:riscv64-ubuntu20-jdk19 setup

docker push fizzed/buildx:riscv64-ubuntu20-jdk19

# x64, alpine 3.11, jdk11
docker build -f setup/Dockerfile.linux_musl --progress=plain \
  --build-arg FROM_IMAGE=amd64/alpine:3.11 \
  --build-arg JAVA_VERSION=11 \
  -t fizzed/buildx:amd64-alpine3.11-jdk11 setup

docker push fizzed/buildx:amd64-alpine3.11-jdk11

# aarch64, alpine 3.11, jdk11
docker build -f setup/Dockerfile.linux_musl --progress=plain \
  --build-arg FROM_IMAGE=arm64v8/alpine:3.11 \
  --build-arg JAVA_VERSION=11 \
  -t fizzed/buildx:arm64v8-alpine3.11-jdk11 setup

docker push fizzed/buildx:arm64v8-alpine3.11-jdk11

# x64 cross build based on fizzed/buildx:amd64-ubuntu16-jdk11
docker build -f setup/Dockerfile.linux-cross-build --progress=plain \
  --build-arg FROM_IMAGE=fizzed/buildx:amd64-ubuntu16-jdk11 \
  -t fizzed/buildx:amd64-ubuntu16-jdk11-cross-build setup

docker push fizzed/buildx:amd64-ubuntu16-jdk11-cross-build

# x64 cross build based on fizzed/buildx:amd64-ubuntu18-jdk11
docker build -f setup/Dockerfile.linux-cross-build --progress=plain \
  --build-arg FROM_IMAGE=fizzed/buildx:amd64-ubuntu18-jdk11 \
  -t fizzed/buildx:amd64-ubuntu18-jdk11-cross-build setup

docker push fizzed/buildx:amd64-ubuntu18-jdk11-cross-build