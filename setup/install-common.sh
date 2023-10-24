#!/bin/sh -e

# Install common packages
apt update
apt -y install apt-utils build-essential libtool autoconf apt-transport-https ca-certificates g++

# Install latest cmake
export UBUNTU_CODENAME=$(grep "UBUNTU_CODENAME" /etc/os-release | sed 's/UBUNTU_CODENAME=//g')
echo "Installing kitware repo for $UBUNTU_CODENAME"
wget -O - https://apt.kitware.com/keys/kitware-archive-latest.asc 2>/dev/null | gpg --dearmor - | tee /usr/share/keyrings/kitware-archive-keyring.gpg >/dev/null
echo "deb [signed-by=/usr/share/keyrings/kitware-archive-keyring.gpg] https://apt.kitware.com/ubuntu/ $UBUNTU_CODENAME main" | tee /etc/apt/sources.list.d/kitware.list >/dev/null
apt update
apt -y install cmake

# Install rust
# https://github.com/cross-rs/cross/tree/main/docker
# https://kerkour.com/rust-cross-compilation
# https://www.docker.com/blog/cross-compiling-rust-code-for-multiple-architectures/
echo "Installing rust toolchain..."
export RUSTUP_HOME=/opt/rustup
export CARGO_HOME=/opt/cargo
export PATH=/opt/cargo/bin:$PATH
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs > install-rust.sh
chmod +x ./install-rust.sh
./install-rust.sh -y
rm ./install-rust.sh
echo "Rust toolchain installed!"

# Fix permissions so libs and includes can be installed by a user during builds
echo "Fixing permissions on lib, include, etc..."
chmod 777 /usr/lib
chmod 777 /usr/include
chmod 777 /usr/share