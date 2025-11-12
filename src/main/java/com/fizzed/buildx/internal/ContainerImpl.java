package com.fizzed.buildx.internal;

import com.fizzed.buildx.*;

public class ContainerImpl implements Container {

    private final String image;
    private final ContainerInfo info;

    public ContainerImpl(String image, ContainerInfo info) {
        this.image = image;
        this.info = info;
    }

    @Override
    public String getImage() {
        return image;
    }

    @Override
    public ContainerInfo getInfo() {
        return info;
    }

    @Override
    public String toString() {
        return this.image;
    }

}