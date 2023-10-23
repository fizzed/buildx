#!/bin/bash -l
set -e

# x64, ubuntu 16.04, jdk11
docker build -f setup/Dockerfile.linux \
  --build-arg FROM_IMAGE=amd64/ubuntu:16.04 \
  --build-arg JAVA_VERSION=11 \
  -t fizzed/buildx:amd64-ubuntu16-jdk11 setup

docker push fizzed/buildx:amd64-ubuntu16-jdk11

# x64, ubuntu 18.04, jdk11
docker build -f setup/Dockerfile.linux \
  --build-arg FROM_IMAGE=amd64/ubuntu:18.04 \
  --build-arg JAVA_VERSION=11 \
  -t fizzed/buildx:amd64-ubuntu18-jdk11 setup

docker push fizzed/buildx:amd64-ubuntu18-jdk11

# x64, ubuntu 20.04, jdk11
docker build -f setup/Dockerfile.linux \
  --build-arg FROM_IMAGE=amd64/ubuntu:20.04 \
  --build-arg JAVA_VERSION=11 \
  -t fizzed/buildx:amd64-ubuntu20-jdk11 setup

docker push fizzed/buildx:amd64-ubuntu20-jdk11

## All versions of ubuntu22
ubuntu22JavaVersions=("21" "17" "11" "8")
for v in ${ubuntu22JavaVersions[*]}; do
  docker build -f setup/Dockerfile.linux \
    --build-arg FROM_IMAGE=amd64/ubuntu:22.04 \
    --build-arg JAVA_VERSION=$v \
    -t fizzed/buildx:amd64-ubuntu22-jdk$v setup

  docker push fizzed/buildx:amd64-ubuntu22-jdk$v
done

# aarch64, ubuntu 16.04, jdk11
docker build -f setup/Dockerfile.linux \
  --build-arg FROM_IMAGE=arm64v8/ubuntu:16.04 \
  --build-arg JAVA_VERSION=11 \
  -t fizzed/buildx:arm64v8-ubuntu16-jdk11 setup

docker push fizzed/buildx:arm64v8-ubuntu16-jdk11

# aarch64, ubuntu 18.04, jdk11
docker build -f setup/Dockerfile.linux \
  --build-arg FROM_IMAGE=arm64v8/ubuntu:18.04 \
  --build-arg JAVA_VERSION=11 \
  -t fizzed/buildx:arm64v8-ubuntu18-jdk11 setup

docker push fizzed/buildx:arm64v8-ubuntu18-jdk11

# armhf, ubuntu 16.04, jdk11
docker build -f setup/Dockerfile.linux \
  --build-arg FROM_IMAGE=arm32v7/ubuntu:16.04 \
  --build-arg JAVA_VERSION=11 \
  -t fizzed/buildx:arm32v7-ubuntu16-jdk11 setup

docker push fizzed/buildx:arm32v7-ubuntu16-jdk11

# armhf, ubuntu 18.04, jdk11
docker build -f setup/Dockerfile.linux \
  --build-arg FROM_IMAGE=arm32v7/ubuntu:18.04 \
  --build-arg JAVA_VERSION=11 \
  -t fizzed/buildx:arm32v7-ubuntu18-jdk11 setup

docker push fizzed/buildx:arm32v7-ubuntu18-jdk11

# armel, debian 11, jdk11
docker build -f setup/Dockerfile.linux \
  --build-arg FROM_IMAGE=arm32v5/debian:11 \
  --build-arg JAVA_VERSION=11 \
  -t fizzed/buildx:arm32v5-debian11-jdk11 setup

docker push fizzed/buildx:arm32v5-debian11-jdk11

# riscv64, ubuntu 20.04, jdk19
docker build -f setup/Dockerfile.linux \
  --build-arg FROM_IMAGE=riscv64/ubuntu:20.04 \
  --build-arg JAVA_VERSION=19 \
  -t fizzed/buildx:riscv64-ubuntu20-jdk19 setup

docker push fizzed/buildx:riscv64-ubuntu20-jdk19

# x64, alpine 3.11, jdk11
docker build -f setup/Dockerfile.linux_musl \
  --build-arg FROM_IMAGE=amd64/alpine:3.11 \
  --build-arg JAVA_VERSION=11 \
  -t fizzed/buildx:amd64-alpine3.11-jdk11 setup

docker push fizzed/buildx:amd64-alpine3.11-jdk11

# aarch64, alpine 3.11, jdk11
docker build -f setup/Dockerfile.linux_musl \
  --build-arg FROM_IMAGE=arm64v8/alpine:3.11 \
  --build-arg JAVA_VERSION=11 \
  -t fizzed/buildx:arm64v8-alpine3.11-jdk11 setup

docker push fizzed/buildx:arm64v8-alpine3.11-jdk11