package de.worldiety.autocd.util;

import de.worldiety.autocd.docker.Docker;
import de.worldiety.autocd.persistence.AutoCD;
import java.io.File;

public class Util {
    public static final String CLOUDIETY_DOMAIN = ".cloudiety.de";

    public static String buildSubdomain() {
        if (isLocal()) {
            return "local-test" + CLOUDIETY_DOMAIN;
        }

        return System.getenv(Environment.CI_PROJECT_NAME.toString()) + "-" + System.getenv(Environment.CI_PROJECT_NAMESPACE.toString()) + CLOUDIETY_DOMAIN;
    }


    public static boolean isLocal() {
        var reg = System.getenv(Environment.CI_REGISTRY.toString());
        return reg == null;
    }


    public static void pushDockerAndSetPath(File dockerfile, AutoCD autoCD) {
        var dockerClient = new Docker();
        var tag = dockerClient.buildAndPushImageFromFile(dockerfile);
        autoCD.setRegistryImagePath(tag);
    }
}
