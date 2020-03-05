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
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.credentials.AccessTokenAuthentication;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final int KUBERNETES_URL = 0;
    private static final int KUBERNETES_TOKEN = 1;
    private static final int CA_CERTIFICATE = 2;
    private static final int BUILD_TYPE = 3;

    public static void main(String[] args) throws IOException {
        ApiClient client;
        client = Config.fromToken(args[KUBERNETES_URL], args[KUBERNETES_TOKEN]).setSslCaCert(new FileInputStream(args[CA_CERTIFICATE]));
        Configuration.setDefaultApiClient(client);

        String name = "autocd.json";
        var autoCD = getAutoCD(name, true);
        var oldAutoCD = getAutoCD("oldautocd.json", false);

        // A new API Client is created. Docker Credentials will be obtained from the Digital Oceans cluster configuration file
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
        // Determines the build Type
        String buildType;
        if (args.length == 4) {
            buildType = args[BUILD_TYPE];
        } else {
            buildType = "dev";
        }

        DockerfileHandler finder = new DockerfileHandler(".");


        //Creat the k8s client, CoreV1Api is needed for the client
        CoreV1Api patchApi = new CoreV1Api(strategicMergePatchClient);
        CoreV1Api api = new CoreV1Api();
        var k8sClient = new K8sClient(api, finder, buildType, patchApi, dockerCredentials);


        /* This method checks for amy images which are referred in the main autoCD object
           If invalid images are found, they will be removed from the cluster.
           An image is invalid if there is no registry image path containing the name of the targeted image.
        */

        if (oldAutoCD != null) {
            var validImageNames = autoCD.getOtherImages().stream()
                    .map(AutoCD::getRegistryImagePath)
                    .collect(Collectors.toList());

            var containsInvalidImages = oldAutoCD.getOtherImages()
                    .stream()
                    .map(AutoCD::getRegistryImagePath)
                    .anyMatch(o -> !validImageNames.contains(o));

            if (containsInvalidImages) {
                oldAutoCD.getOtherImages().forEach(image -> {
                    setServiceNameForOtherImages(oldAutoCD, image);
                    removeWithDependencies(image, k8sClient);
                });
            }
        }

        populateRegistryImagePath(autoCD, buildType, finder);
        populateSubdomain(autoCD, buildType, autoCD.getSubdomainsEnv());
        populateContainerPort(autoCD, finder);

        /* Checks if the app should be hosted on the cluster, if one decides to abandon the app, this method will
            remove it if the tag 'isShouldHost' is set correctly (false)
        */

        if (!autoCD.isShouldHost()) {
            log.info("Service is being removed from k8s.");
            removeWithDependencies(autoCD, k8sClient);

            log.info("Not deploying to k8s because autocd is set to no hosting");
            return;
        }

        if (!autoCD.isDockerOnly()) {
            deployWithDependencies(autoCD, k8sClient, buildType);
            log.info("Deployed to k8s with subdomain: " + autoCD.getSubdomains());
        } else {
            log.info("Done.");
        }
    }

    /**
     * If its a vue Project, it requires Port 80. Because its simpler to check for .vue than changing the nginx
     * configuration, this method will change the container port from the default value 8080 to 80.
     *
     * @param autoCD
     * @param finder
     */
    private static void populateContainerPort(AutoCD autoCD, DockerfileHandler finder) {
        if (autoCD.getContainerPort() == 8080 && finder.getFileType().equals(FileType.VUE)) {
            autoCD.setContainerPort(80);
        }
    }

    /**
     * If there is no image on the registry, creates new Dockerfile, builds an images and pushes it to the registry
     * and sets its path in the autoCD object.
     *
     * @param autoCD
     * @param buildType
     * @param finder
     */
    private static void populateRegistryImagePath(AutoCD autoCD, String buildType, DockerfileHandler finder) {
        if (autoCD.getRegistryImagePath() == null || autoCD.getRegistryImagePath().isEmpty()) {
            var dockerFile = new File("Dockerfile");

            if (!dockerFile.exists()) {
                log.info("Using Dockerfile provided by AutoCD");
                finder.findDockerConfig(autoCD, buildType).ifPresent(config -> {
                    Util.pushDockerAndSetPath(config, autoCD, buildType);
                });
            } else {
                Util.pushDockerAndSetPath(dockerFile.getAbsoluteFile(), autoCD, buildType);
            }
        }
    }

    /**
     * If the autoCD object can not be found, this method creates a new autoCD object and returns it.
     * If there is one, the autoCD object will get its parameters from an exiting configuration file.
     *
     * @param name
     * @param createIfNotExists
     * @return
     */
    private static AutoCD getAutoCD(String name, boolean createIfNotExists) throws FileNotFoundException {
        var autocdConfigFile = new File(name);
        AutoCD autoCD;
        if (!autocdConfigFile.exists() && createIfNotExists) {
            autoCD = new AutoCD();
        } else {
            Gson gson = new Gson();
            autoCD = gson.fromJson(new FileReader(autocdConfigFile), AutoCD.class);
        }
        return autoCD;
    }

    /**
     * This method looks for subdomains within the autoCD object, if there are any, the subdomain will be the one with the
     * matching build type.
     * If autoCD does not have any subdomains, this method will build one out of the build type and an hashed version
     * of the registry image path.
     *
     * @param autoCD
     * @param buildType
     * @param subdomains
     */
    private static void populateSubdomain(AutoCD autoCD, String buildType, Map<String, List<String>> subdomains) {
        if (autoCD.getSubdomainsEnv() != null && subdomains.keySet().size() != 0) {
            autoCD.setSubdomains(autoCD.getSubdomainsEnv().get(buildType));
        }

        if (autoCD.getSubdomains() == null || autoCD.getSubdomains().isEmpty()) {
            autoCD.setSubdomains(List.of(Util.buildSubdomain(buildType, Util.hash(autoCD.getIdentifierRegistryImagePath()).substring(0, 5))));
        }
    }

    /**
     * If a service should be removed, this method checks for other services, depending on the one which will be removed from the
     * cluster. Dependencies can be found in the autoCD class variable called otherImages. The method proceeds recursively
     * and removes all depending services.
     *
     * @param autoCD
     * @param k8sClient
     */
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

    /**
     * Creates a name for a depending service out of the project name and a hashed registry image path from the main service
     *
     * @param main
     * @param other
     */
    private static void setServiceNameForOtherImages(AutoCD main, AutoCD other) {
        if (other.getServiceName() == null) {
            other.setServiceName(Util.hash(
                    System.getenv(Environment.CI_PROJECT_NAME.toString()) + main.getIdentifierRegistryImagePath()).substring(0, 20)
            );
        }
    }

    /**
     * If there are dependencies found within the main service, those services will be deployed as well.
     *
     * @param autoCD
     * @param k8sClient
     * @param buildType
     */
    private static void deployWithDependencies(AutoCD autoCD, K8sClient k8sClient, String buildType) {
        validateConfig(autoCD);
        if (!autoCD.getOtherImages().isEmpty()) {
            autoCD.getOtherImages().forEach(config -> {
                populateSubdomain(config, buildType, autoCD.getSubdomainsEnv());

                if (!config.getOtherImages().isEmpty()) {
                    deployWithDependencies(config, k8sClient, buildType);
                }
                setServiceNameForOtherImages(autoCD, config);
                k8sClient.deployToK8s(config);
            });
        }

        k8sClient.deployToK8s(autoCD);
    }

    /**
     * Note that autoCD does not support retaining volumes if the replica set is greater than 1.
     *
     * @param autoCD
     */
    private static void validateConfig(AutoCD autoCD) {
        if (autoCD.getReplicas() > 1) {
            if (autoCD.getVolumes().stream().anyMatch(Volume::isRetainVolume)) {
                throw new IllegalArgumentException("AutoCD config is invalid, if using more than 1 replica retainVolume has to be set to false");
            }
        }
    }
}



