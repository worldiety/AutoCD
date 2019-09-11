package de.worldiety.autocd;

import de.worldiety.autocd.persistence.AutoCD;
import de.worldiety.autocd.persistence.FileFinder;
import de.worldiety.autocd.util.Environment;
import de.worldiety.autocd.util.FileType;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.KubeConfig;
import java.io.FileReader;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws ApiException, IOException {
        FileFinder finder = new FileFinder(".");
        System.out.println(finder.findDockerConfig());

        ApiClient client = Config.fromConfig(KubeConfig.loadKubeConfig(new FileReader("/Users/lgrahmann/Downloads/kuberniety-kubeconfig.yaml")));
        Configuration.setDefaultApiClient(client);

        CoreV1Api api = new CoreV1Api();

        var autocd = new AutoCD();
        autocd.setRegistryImagePath("registry.worldiety.net/lgrahmann/autocd-test-java");
        autocd.setSubdomain("localdemo.cloudiety.de");

        var k8sClient = new K8sClient(api);
        try {
            k8sClient.deployToK8s(autocd);
        } catch (ApiException e) {
            log.warn(e.getResponseBody());
        }

        //var docker = new Docker();
        /*
        finder.findDockerConfig().ifPresent(configFile -> {
            docker.buildImageFromFile(configFile);

        });
        */
    }
}



