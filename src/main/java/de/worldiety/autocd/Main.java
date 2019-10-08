package de.worldiety.autocd;

import com.google.gson.Gson;
import de.worldiety.autocd.docker.DockerfileHandler;
import de.worldiety.autocd.k8s.K8sClient;
import de.worldiety.autocd.persistence.AutoCD;
import de.worldiety.autocd.persistence.Volume;
import de.worldiety.autocd.util.DockerconfigBuilder;
import de.worldiety.autocd.util.Environment;
import de.worldiety.autocd.util.FileType;
import de.worldiety.autocd.util.Util;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.credentials.AccessTokenAuthentication;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws IOException {
        DockerfileHandler finder = new DockerfileHandler(".");
        ApiClient client;


        client = Config.fromToken(args[0], args[1]).setSslCaCert(new FileInputStream(args[2]));
        Configuration.setDefaultApiClient(client);

        var autocdConfigFile = new File("autocd.json");
        AutoCD autoCD;
        if (!autocdConfigFile.exists()) {
            autoCD = new AutoCD();
        } else {
            Gson gson = new Gson();
            autoCD = gson.fromJson(new FileReader(autocdConfigFile), AutoCD.class);
        }


        String buildType;
        if (args.length == 4) {
            buildType = args[3];
        } else {
            buildType = "dev";
        }

        if (autoCD.getRegistryImagePath() == null || autoCD.getRegistryImagePath().isEmpty()) {
            var dockerFile = new File("Dockerfile");

            if (!dockerFile.exists()) {
                finder.findDockerConfig().ifPresent(config -> {
                    Util.pushDockerAndSetPath(config, autoCD, buildType);
                });
            } else {
                Util.pushDockerAndSetPath(dockerFile.getAbsoluteFile(), autoCD, buildType);
            }
        }

        if (autoCD.getSubdomain() == null || autoCD.getSubdomain().isEmpty()) {
            autoCD.setSubdomain(Util.buildSubdomain(buildType, Util.hash(autoCD.getRegistryImagePath()).substring(0, 5)));
        }

        if (autoCD.getContainerPort() == 8080 && finder.getFileType().equals(FileType.VUE)) {
            autoCD.setContainerPort(80);
        }


        ApiClient strategicMergePatchClient = ClientBuilder.standard()
                .setBasePath(args[0])
                .setVerifyingSsl(true)
                .setAuthentication(new AccessTokenAuthentication(args[1]))
                .setOverridePatchFormat(V1Patch.PATCH_FORMAT_JSON_PATCH)
                .build()
                .setSslCaCert(new FileInputStream(args[2]));

        var dockerCredentials = DockerconfigBuilder.getDockerConfig(
                System.getenv(Environment.CI_REGISTRY.toString()),
                System.getenv(Environment.K8S_REGISTRY_USER_NAME.toString()),
                System.getenv(Environment.K8S_REGISTRY_USER_TOKEN.toString())
        );

        CoreV1Api patchApi = new CoreV1Api(strategicMergePatchClient);
        CoreV1Api api = new CoreV1Api();
        var k8sClient = new K8sClient(api, finder, buildType, patchApi, dockerCredentials);
        if (!autoCD.isShouldHost()) {
            removeWithDependencies(autoCD, k8sClient);
            log.info("Service is being removed from k8s.");
            return;
        }


        deployWithDependencies(autoCD, k8sClient, buildType);
        log.info("Deployed to k8s with subdomain: " + autoCD.getSubdomain());
    }

    private static void removeWithDependencies(AutoCD autoCD, K8sClient k8sClient) {
        if (!autoCD.getOtherImages().isEmpty()) {
            autoCD.getOtherImages().forEach(config -> {
                setServiceNameForOtherImages(autoCD, config);
                if (!config.getOtherImages().isEmpty()) {
                    removeWithDependencies(config, k8sClient);
                }
            });
        }

        k8sClient.removeDeploymentFromK8s(autoCD);
    }

    private static void setServiceNameForOtherImages(AutoCD main, AutoCD other) {
        if (other.getServiceName() == null) {
            other.setServiceName(Util.hash(
                    System.getenv(Environment.CI_PROJECT_NAME.toString()) + main.getRegistryImagePath()).substring(0, 20)
            );
        }
    }

    private static void deployWithDependencies(AutoCD autoCD, K8sClient k8sClient, String buildType) {
        validateConfig(autoCD);
        if (!autoCD.getOtherImages().isEmpty()) {
            autoCD.getOtherImages().forEach(config -> {
                if (config.getSubdomain() == null || config.getSubdomain().isEmpty()) {
                    config.setSubdomain(Util.buildSubdomain(buildType, Util.hash(config.getRegistryImagePath()).substring(0, 5)));
                }

                if (!config.getOtherImages().isEmpty()) {
                    deployWithDependencies(config, k8sClient, buildType);
                }
                setServiceNameForOtherImages(autoCD, config);
                k8sClient.deployToK8s(config);
            });
        }

        k8sClient.deployToK8s(autoCD);
    }

    private static void validateConfig(AutoCD autoCD) {
        if (autoCD.getReplicas() > 1) {
            if (autoCD.getVolumes().stream().anyMatch(Volume::isRetainVolume)) {
                throw new IllegalArgumentException("AutoCD config is invalid, if using more than 1 replica retainVolume has to be set to false");
            }
        }
    }
}



