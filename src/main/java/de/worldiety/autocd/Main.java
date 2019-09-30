package de.worldiety.autocd;

import com.google.gson.Gson;
import de.worldiety.autocd.docker.DockerfileHandler;
import de.worldiety.autocd.k8s.K8sClient;
import de.worldiety.autocd.persistence.AutoCD;
import de.worldiety.autocd.util.Environment;
import de.worldiety.autocd.util.FileType;
import de.worldiety.autocd.util.Util;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws ApiException, IOException {
        DockerfileHandler finder = new DockerfileHandler(".");
        ApiClient client;

        //TODO: configurable
        if (Util.isLocal()) {
            client = Config.fromConfig(KubeConfig.loadKubeConfig(new FileReader("/Users/lgrahmann/Downloads/kuberniety-kubeconfig.yaml")));
        } else {
            client = Config.fromToken(args[0], args[1]).setSslCaCert(new FileInputStream(args[2]));
        }

        Configuration.setDefaultApiClient(client);

        var autocdConfigFile = new File("autocd.json");
        AutoCD autoCD;
        if (!autocdConfigFile.exists()) {
            autoCD = new AutoCD();
        } else {
            Gson gson = new Gson();
            autoCD = gson.fromJson(new FileReader(autocdConfigFile), AutoCD.class);
        }

        if (autoCD.getRegistryImagePath() == null || autoCD.getRegistryImagePath().isEmpty()) {
            var dockerFile = new File("Dockerfile");

            if (!dockerFile.exists()) {
                finder.findDockerConfig().ifPresent(config -> {
                    Util.pushDockerAndSetPath(config, autoCD);
                });
            } else {
                Util.pushDockerAndSetPath(dockerFile.getAbsoluteFile(), autoCD);
            }
        }

        String buildType;
        if (args.length == 4) {
            buildType = args[3];
        } else {
            buildType = "dev";
        }

        if (autoCD.getSubdomain() == null || autoCD.getSubdomain().isEmpty()) {
            autoCD.setSubdomain(Util.buildSubdomain(buildType));
        }

        if (autoCD.getContainerPort() == 8080 && finder.getFileType().equals(FileType.VUE)) {
            autoCD.setContainerPort(80);
        }

        CoreV1Api api = new CoreV1Api();
        var k8sClient = new K8sClient(api, finder, buildType);
        if (!autoCD.isShouldHost()) {
            if (!autoCD.getOtherImages().isEmpty()) {
                autoCD.getOtherImages().forEach(config -> {
                    setServiceNameForOtherImages(autoCD, config);
                    k8sClient.removeFromK8s(config);
                });
            }

            k8sClient.removeFromK8s(autoCD);
            log.info("Service is being removed from k8s.");
            return;
        }

        if (!autoCD.getOtherImages().isEmpty()) {
            autoCD.getOtherImages().forEach(config -> {
                setServiceNameForOtherImages(autoCD, config);
                k8sClient.deployToK8s(config);
            });
        }

        k8sClient.deployToK8s(autoCD);
        log.info("Deployed to k8s with subdomain: " + autoCD.getSubdomain());
    }

    private static void setServiceNameForOtherImages(AutoCD main, AutoCD other) {
        if (other.getServiceName() == null) {
            other.setServiceName(Util.hash(
                    System.getenv(Environment.CI_PROJECT_NAME.toString()) + main.getRegistryImagePath())
            );
        }
    }

}



