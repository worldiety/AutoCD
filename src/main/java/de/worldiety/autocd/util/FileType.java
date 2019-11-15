package de.worldiety.autocd.util;

import org.jetbrains.annotations.Contract;

public enum FileType {
    JAVA("java", "openjdk-12-builder", "openjdk-12-prod", "RUN ./gradlew build\n"),
    GO("go", "go-1.13-builder", "go-1.13-prod", "RUN go build -o app . \n"),
    VUE("vue", "vue-builder", "vue-prod", "RUN npm run build\n"),
    NUXT("vue", "nuxt-builder", "nuxt-prod", "RUN npm i && npm run build"),
    EISEN("eisen", "vue-builder", "vue-prod", "RUN npm run build\n"),
    OTHER("other");

    private final String name;
    private String defaultBuild;
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

    FileType(String name, String dockerConfig, String finalDocker, String defaultBuild) {
        this.name = name;
        this.dockerConfig = dockerConfig;
        this.finalDocker = finalDocker;
        this.defaultBuild = defaultBuild;
    }

    public String getDefaultBuild() {
        return defaultBuild;
    }

    public void setDefaultBuild(String defaultBuild) {
        this.defaultBuild = defaultBuild;
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
