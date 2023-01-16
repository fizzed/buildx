#!/bin/sh

BUILDOS=$1
BUILDARCH=$2

if [ -z "${BUILDOS}" ] || [ -z "${BUILDOS}" ]; then
  echo "Usage: script [os] [arch]"
  exit 1
fi

echo "================================================ Cross Build Environment ================================================"
echo "Setting up cross build environment..."
echo ""

if [ $BUILDOS = "linux" ]; then
  if [ $BUILDARCH = "arm64" ]; then
    BUILDTARGET=aarch64-linux-gnu
  elif [ $BUILDARCH = "armhf" ]; then
    # its odd how raspbian/rpios both call their architecture "armhf" when its really not
    BUILDTARGET=arm-linux-gnueabihf
  elif [ $BUILDARCH = "armel" ]; then
    BUILDTARGET=arm-linux-gnueabi
  elif [ $BUILDARCH = "riscv64" ]; then
    BUILDTARGET=riscv64-linux-gnu
  fi
  export SYSROOT="/usr/${BUILDTARGET}"
  if [ $BUILDARCH = "x64" ]; then
    BUILDTARGET=x86_64-linux-gnu
    export SYSROOT="/usr"
  fi
elif [ $BUILDOS = "linux_musl" ]; then
  # https://stackoverflow.com/questions/39936341/how-do-i-use-a-sysroot-with-autoconf
  if [ $BUILDARCH = "x64" ]; then
    BUILDTARGET=x86_64-linux-musl
  elif [ $BUILDARCH = "arm64" ]; then
    BUILDTARGET=aarch64-linux-musl
  fi
  export SYSROOT="/opt/${BUILDTARGET}-cross/${BUILDTARGET}"
  export CFLAGS="--sysroot=${SYSROOT} $CFLAGS"
  export CXXFLAGS="--sysroot=${SYSROOT} $CFLAGS"
  export LDFLAGS="--sysroot=${SYSROOT} -L${SYSROOT}/usr/lib $CFLAGS"
fi

if [ -z "$BUILDTARGET" ]; then
  echo "Unsupported os-arch: $BUILDOS-$BUILDARCH"
  exit 1
fi

export BUILDTARGET
export CC=$BUILDTARGET-gcc
export CXX=$BUILDTARGET-g++
export STRIP=$BUILDTARGET-strip
export AR=$BUILDTARGET-ar
export RANLIB=$BUILDTARGET-ranlib

echo "BUILDTARGET: ${BUILDTARGET}"
echo "SYSROOT: ${SYSROOT}"
echo "CFLAGS: ${CFLAGS}"
echo "CXXFLAGS: ${CXXFLAGS}"
echo "LDFLAGS: ${LDFLAGS}"
echo "CC: ${CC}"
echo "CXX: ${CXX}"
echo "AR: ${AR}"
echo "RANLIB: ${RANLIB}"
echo "STRIP: ${STRIP}"
echo "========================================================================================================================="