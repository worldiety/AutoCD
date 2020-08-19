package de.worldiety.autocd.util;

import de.worldiety.autocd.docker.Docker;
import de.worldiety.autocd.persistence.AutoCD;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

public class Util {
    public static final String CLOUDIETY_DOMAIN = ".cloudiety.de";

    public static String buildSubdomain(String buildType, String hash) {
        if (isLocal()) {
            return "local-test" + CLOUDIETY_DOMAIN;
        }

        return System.getenv(Environment.CI_PROJECT_NAME.toString()) +
                "-" +
                System.getenv(Environment.CI_PROJECT_NAMESPACE.toString()).replaceAll("/", "--") +
                "-" +
                buildType +
                "-" +
                hash +
                CLOUDIETY_DOMAIN;
    }


    public static boolean isLocal() {
        var reg = System.getenv(Environment.CI_REGISTRY.toString());
        return reg == null;
    }


    public static void pushDockerAndSetPath(File dockerfile, AutoCD autoCD, String buildType) {
        var dockerClient = new Docker();
        var tag = dockerClient.buildAndPushImageFromFile(dockerfile, buildType);
        autoCD.setRegistryImagePath(tag);
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static String hash(String toHash) {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        byte[] encodedhash = Objects.requireNonNull(digest).digest(
                toHash.getBytes(StandardCharsets.UTF_8));

        return Util.bytesToHex(encodedhash);
    }

    public static String slugify(String toSlug) {
        return slugify(toSlug, 255);
    }

    public static String slugify(String toSlug, int maxLength) {
        if (toSlug == null) {
            return null;
        }
        String slugified = toSlug.toLowerCase().replaceAll("[^a-z0-9\\-]", "");
        return slugified.substring(0, Integer.min(slugified.length(), maxLength));
    }
}
