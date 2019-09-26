package de.worldiety.autocd;

import com.google.gson.Gson;
import de.worldiety.autocd.docker.DockerfileHandler;
import de.worldiety.autocd.k8s.K8sClient;
import de.worldiety.autocd.persistence.AutoCD;
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

        if (autoCD.getSubdomain() == null || autoCD.getSubdomain().isEmpty()) {
            autoCD.setSubdomain(Util.buildSubdomain());
        }

        CoreV1Api api = new CoreV1Api();
        var k8sClient = new K8sClient(api);
        if (!autoCD.isShouldHost()) {
            k8sClient.removeFromK8s(autoCD);
            log.info("Service is being removed from k8s.");
            return;
        }

        try {
            k8sClient.deployToK8s(autoCD);
            log.info("Deployed to k8s with subdomain: " + autoCD.getSubdomain());
        } catch (ApiException e) {
            log.warn("not caught " + e);
            e.printStackTrace();
        }
    }

}



