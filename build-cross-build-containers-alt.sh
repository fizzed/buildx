#!/bin/sh -l
set -e

docker build -f setup/Dockerfile.buildx \
  --build-arg FROM_IMAGE=fizzed/buildx:amd64-ubuntu16-jdk11 \
  -t fizzed/buildx:amd64-ubuntu16-jdk11-buildx setup

docker build -f setup/Dockerfile.buildx \
  --build-arg FROM_IMAGE=fizzed/buildx:amd64-ubuntu18-jdk11 \
  -t fizzed/buildx:amd64-ubuntu18-jdk11-buildx setup

docker build -f setup/Dockerfile.buildx-linux-x64 \
  --build-arg FROM_IMAGE=fizzed/buildx:amd64-ubuntu16-jdk11-buildx \
  -t fizzed/buildx:amd64-ubuntu16-jdk11-buildx-linux-x64 setup

docker build -f setup/Dockerfile.buildx-linux-arm64 \
  --build-arg FROM_IMAGE=fizzed/buildx:amd64-ubuntu16-jdk11-buildx \
  -t fizzed/buildx:amd64-ubuntu16-jdk11-buildx-linux-arm64 setup

# riscv64 is only on ubuntu 18+
docker build -f setup/Dockerfile.buildx-linux-riscv64 \
  --build-arg FROM_IMAGE=fizzed/buildx:amd64-ubuntu18-jdk11-buildx \
  -t fizzed/buildx:amd64-ubuntu18-jdk11-buildx-linux-riscv64 setup

docker build -f setup/Dockerfile.buildx-linux-musl-x64 \
  --build-arg FROM_IMAGE=fizzed/buildx:amd64-ubuntu16-jdk11-buildx \
  -t fizzed/buildx:amd64-ubuntu16-jdk11-buildx-linux-musl-x64 setup



#docker push fizzed/buildx:amd64-ubuntu16-jdk11-cross-build
#
## x64 cross build based on fizzed/buildx:amd64-ubuntu18-jdk11
#docker build -f setup/Dockerfile.linux-cross-build \
#  --build-arg FROM_IMAGE=fizzed/buildx:amd64-ubuntu18-jdk11 \
#  -t fizzed/buildx:amd64-ubuntu18-jdk11-cross-build setup
#
#docker push fizzed/buildx:amd64-ubuntu18-jdk11-cross-build
