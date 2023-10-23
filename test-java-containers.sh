#!/bin/bash -l
set -e

containers=(
#  "fizzed/buildx:amd64-ubuntu16-jdk11"
#  "fizzed/buildx:amd64-ubuntu22-jdk17"
#  "fizzed/buildx:arm32v7-ubuntu16-jdk11"
#  "fizzed/buildx:arm32v7-ubuntu18-jdk11"
#  "fizzed/buildx:arm32v5-debian11-jdk11"
  "fizzed/buildx:amd64-alpine3.11-jdk11"
)

for container in ${containers[*]}; do
  echo "########### Start Testing $container ###########"
  echo ""
  docker run -it "$container" uname -a
  echo ""
  docker run -it "$container" which javac
  echo ""
  docker run -it "$container" java -version
  echo ""
  docker run -it "$container" mvn --version
  echo ""
  docker run -it "$container" which blaze
    echo ""
  echo "########### End Testing $container ###########"
done