#!/bin/sh -l
set -e

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
