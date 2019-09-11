package de.worldiety.autocd.util;

import org.jetbrains.annotations.Contract;

public enum FileType {
    JAVA("java", "openjdk-12-docker", "spring-12-docker"), GO("go", "go-docker"), VUE("vue", "vue-docker"), OTHER("other");

    private final String name;
    private String dockerConfig;
    private String finalDocker;

    @Contract(pure = true)
    FileType(String name) {
        this.name = name;
    }

    @Contract(pure = true)
    FileType(String name, String dockerConfig) {
        this.name = name;
        this.dockerConfig = dockerConfig;
    }

    @Contract(pure = true)
    FileType(String name, String dockerConfig, String finalDocker) {
        this.name = name;
        this.dockerConfig = dockerConfig;
        this.finalDocker = finalDocker;
    }

    @Contract(pure = true)
    public String getName() {
        return name;
    }

    @Contract(pure = true)
    public String getDockerConfig() {
        return dockerConfig;
    }

    public void setDockerConfig(String dockerConfig) {
        this.dockerConfig = dockerConfig;
    }

    @Contract(pure = true)
    public String getFinalDocker() {
        return finalDocker;
    }

    public void setFinalDocker(String finalDocker) {
        this.finalDocker = finalDocker;
    }
}
