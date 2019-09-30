package de.worldiety.autocd.k8s;

import static de.worldiety.autocd.util.Util.hash;
import static de.worldiety.autocd.util.Util.isLocal;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import de.worldiety.autocd.docker.DockerfileHandler;
import de.worldiety.autocd.persistence.AutoCD;
import de.worldiety.autocd.persistence.Volume;
import de.worldiety.autocd.util.Environment;
import de.worldiety.autocd.util.FileType;
import de.worldiety.autocd.util.Util;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.ExtensionsV1beta1Api;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.ExtensionsV1beta1DeploymentSpec;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1ContainerBuilder;
import io.kubernetes.client.models.V1ContainerPort;
import io.kubernetes.client.models.V1LabelSelector;
import io.kubernetes.client.models.V1LocalObjectReference;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolumeClaim;
import io.kubernetes.client.models.V1PersistentVolumeClaimSpecBuilder;
import io.kubernetes.client.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.models.V1PodSpec;
import io.kubernetes.client.models.V1PodTemplateSpec;
import io.kubernetes.client.models.V1ResourceRequirementsBuilder;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServicePort;
import io.kubernetes.client.models.V1ServiceSpec;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
import io.kubernetes.client.models.V1VolumeMountBuilder;
import io.kubernetes.client.models.V1beta1HTTPIngressPathBuilder;
import io.kubernetes.client.models.V1beta1HTTPIngressRuleValueBuilder;
import io.kubernetes.client.models.V1beta1Ingress;
import io.kubernetes.client.models.V1beta1IngressBackendBuilder;
import io.kubernetes.client.models.V1beta1IngressRuleBuilder;
import io.kubernetes.client.models.V1beta1IngressSpecBuilder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class K8sClient {
    private static final Logger log = LoggerFactory.getLogger(K8sClient.class);
    private final CoreV1Api api;
    private final DockerfileHandler finder;

    @Contract(pure = true)
    public K8sClient(CoreV1Api api, DockerfileHandler finder) {
        this.api = api;
        this.finder = finder;
    }

    public void deployToK8s(AutoCD autoCD) {
        deploy(autoCD);
    }

    @SuppressWarnings("unused")
    private void deleteNamespace(@NotNull V1Namespace namespace) {
        try {
            api.deleteNamespace(namespace.getMetadata().getName(), "true", null, null, null, null, "Foreground");
        } catch (ApiException e) {
            log.warn("Could not delete namespace", e);
        } catch (JsonSyntaxException e) {
            ignoreGoogleParsingError(e);
        }
    }

    private void createClaims(@NotNull List<V1PersistentVolumeClaim> claims) {
        claims.forEach(this::applyClaim);
    }

    private void deleteClaims(@NotNull List<V1PersistentVolumeClaim> claims) {
        claims.forEach(this::applyDeleteClaim);
    }

    private void applyDeleteClaim(V1PersistentVolumeClaim claim) {
        try {
            //api.patchNamespacedPersistentVolumeClaim(claim.getMetadata().getName(), claim.getMetadata().getNamespace(), )
            api.deleteNamespacedPersistentVolumeClaim(claim.getMetadata().getName(), claim.getMetadata().getNamespace(), null, null, null, null, null, "Foreground");
        } catch (ApiException e) {
            retry(claim, this::applyDeleteClaim, e);
        } catch (JsonSyntaxException e) {
            ignoreGoogleParsingError(e);
        }
    }

    private void applyClaim(V1PersistentVolumeClaim claim) {
        try {
            api.createNamespacedPersistentVolumeClaim(claim.getMetadata().getNamespace(), claim, null, null, null);
        } catch (ApiException e) {
            retry(claim, this::applyClaim, e);
        } catch (JsonSyntaxException e) {
            ignoreGoogleParsingError(e);
        }
    }

    private void deleteDeployment(@NotNull ExtensionsV1beta1Deployment deployment) {
        ExtensionsV1beta1Api extensionsV1beta1Api = getExtensionsV1beta1Api();
        try {
            extensionsV1beta1Api.deleteNamespacedDeployment(deployment.getMetadata().getName(), deployment.getMetadata().getNamespace(), "true", null, null, null, null, "Foreground");
        } catch (ApiException e) {
            log.warn("Could not delete deployment", e);
        } catch (JsonSyntaxException e) {
            ignoreGoogleParsingError(e);
        }
    }

    private void deleteService(@NotNull V1Service service) {
        try {
            api.deleteNamespacedService(service.getMetadata().getName(), service.getMetadata().getNamespace(), null, null, null, null, null, "Foreground");
        } catch (ApiException e) {
            log.warn("Could not delete service", e);
        } catch (JsonSyntaxException e) {
            ignoreGoogleParsingError(e);
        }
    }

    private void deleteIngress(@NotNull V1beta1Ingress ingress) {
        ExtensionsV1beta1Api extensionsV1beta1Api = getExtensionsV1beta1Api();
        try {
            extensionsV1beta1Api.deleteNamespacedIngress(ingress.getMetadata().getName(), ingress.getMetadata().getNamespace(), null, null, null, null, null, "Foreground");
        } catch (ApiException e) {
            log.error("Could not delete ingress", e);
        } catch (JsonSyntaxException e) {
            ignoreGoogleParsingError(e);
        }
    }

    /**
     * https://github.com/kubernetes-client/java/issues/86
     *
     * @param ignored ignored
     */
    @SuppressWarnings("unused")
    private void ignoreGoogleParsingError(JsonSyntaxException ignored) {
        //No-op
    }

    private void deploy(AutoCD autoCD) {
        var ingress = getIngress(autoCD);
        deleteIngress(ingress);
        var service = getService(autoCD);
        deleteService(service);
        var deployment = getDeployment(autoCD);
        deleteDeployment(deployment);
        var claims = getPersistentVolumeClaims(autoCD);
        deleteClaims(claims);
        var nameSpace = getNamespace();

        createNamespace(nameSpace);
        createClaims(claims);
        createDeployment(deployment);
        createService(service);

        if (autoCD.isPubliclyAccessible()) {
            createIngress(ingress);
        }
    }

    private void createIngress(V1beta1Ingress ingress) {
        ExtensionsV1beta1Api extensionsV1beta1Api = getExtensionsV1beta1Api();
        try {
            extensionsV1beta1Api.createNamespacedIngress(ingress.getMetadata().getNamespace(), ingress, false, "true", null);
        } catch (ApiException e) {
            retry(ingress, this::createIngress, e);
        }
    }

    @NotNull
    private ExtensionsV1beta1Api getExtensionsV1beta1Api() {
        var extensionsV1beta1Api = new ExtensionsV1beta1Api();
        extensionsV1beta1Api.setApiClient(api.getApiClient());
        return extensionsV1beta1Api;
    }

    private void createService(V1Service service) {
        try {
            api.createNamespacedService(service.getMetadata().getNamespace(), service, false, "true", null);
        } catch (ApiException e) {
            retry(service, this::createService, e);
        }


    }

    private void createDeployment(ExtensionsV1beta1Deployment deployment) {
        ExtensionsV1beta1Api extensionsV1beta1Api = getExtensionsV1beta1Api();
        try {
            extensionsV1beta1Api.createNamespacedDeployment(deployment.getMetadata().getNamespace(), deployment, false, "true", null);
        } catch (ApiException e) {
            retry(deployment, this::createDeployment, e);
        }


    }

    private void createNamespace(V1Namespace nameSpace) {
        try {
            api.createNamespace(nameSpace, false, "true", null);
        } catch (ApiException e) {
            retry(nameSpace, this::createNamespace, e);
        }


    }

    private String getPVCName(Volume volume, AutoCD autoCD) {
        var str = getNamespaceString() + "-" + getName() + "-" + autoCD.getRegistryImagePath() + "-" + autoCD.getVolumes().indexOf(volume) + "-claim";
        return hash(str).substring(0, 20);
    }

    private List<V1PersistentVolumeClaim> getPersistentVolumeClaims(AutoCD autoCD) {
        return autoCD.getVolumes().stream().map(volume -> {
            var pvc = new V1PersistentVolumeClaim();
            pvc.setKind("PersistentVolumeClaim");
            var meta = getNamespacedMeta();
            meta.setName(getPVCName(volume, autoCD));
            pvc.setMetadata(meta);
            var spec = new V1PersistentVolumeClaimSpecBuilder()
                    .withAccessModes("ReadWriteOnce")
                    .withResources(new V1ResourceRequirementsBuilder()
                            .withRequests(Map.of("storage", new Quantity(volume.getVolumeSize())))
                            .build())
                    .withStorageClassName("do-block-storage")
                    .build();

            pvc.setSpec(spec);

            return pvc;
        }).collect(Collectors.toList());
    }

    @NotNull
    private V1beta1Ingress getIngress(@NotNull AutoCD autoCD) {
        var ingress = new V1beta1Ingress();
        ingress.setKind("Ingress");
        var meta = getNamespacedMeta();
        meta.setName(getNamespaceString() + "-" + getName() + "-ingress");

        /*ExtensionsV1beta1Api extensionsV1beta1Api = getExtensionsV1beta1Api();
        try {
            var ingresses = extensionsV1beta1Api.listIngressForAllNamespaces(null, null, null, null, null, null, null, null, null);
            var contained = ingresses.getItems()
                    .stream()
                    .filter(it -> !it.getMetadata().getNamespace().equals(meta.getNamespace()))
                    .anyMatch(it ->
                            it.getSpec().getRules().stream()
                                    .anyMatch(rule -> rule.getHost().equals(autoCD.getSubdomain())));
        } catch (ApiException e) {
            e.printStackTrace();
        }*/

        var spec = new V1beta1IngressSpecBuilder()
                .withRules(new V1beta1IngressRuleBuilder()
                        .withHost(autoCD.getSubdomain())
                        .withHttp(new V1beta1HTTPIngressRuleValueBuilder()
                                .withPaths(new V1beta1HTTPIngressPathBuilder().withPath("/")
                                        .withBackend(new V1beta1IngressBackendBuilder()
                                                .withServiceName(getNamespaceString() + "-" + getName() + "-service")
                                                .withServicePort(new IntOrString(autoCD.getServicePort()))
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build();

        ingress.setSpec(spec);
        ingress.setMetadata(meta);

        return ingress;
    }

    @NotNull
    private V1Service getService(@NotNull AutoCD autoCD) {
        var service = new V1Service();
        service.setKind("Service");
        var meta = getNamespacedMeta();
        if (autoCD.getServiceName() != null) {
            meta.setName(autoCD.getServiceName());
        } else {
            meta.setName(getNamespaceString() + "-" + getName() + "-service");
        }

        var spec = new V1ServiceSpec();
        spec.setSelector(Map.of("k8s-app", getK8sApp(autoCD)));
        var port = new V1ServicePort();
        port.setName("web");
        port.setPort(autoCD.getServicePort());
        port.setTargetPort(new IntOrString(autoCD.getContainerPort()));
        spec.setPorts(List.of(port));

        service.setSpec(spec);
        service.setMetadata(meta);

        return service;
    }

    private String getK8sApp(AutoCD autoCD) {
        return getNamespaceString() + "-" + getName() + "-" + Util.hash(autoCD.getRegistryImagePath()).substring(0, 20);
    }

    @NotNull
    private ExtensionsV1beta1Deployment getDeployment(@NotNull AutoCD autoCD) {
        var meta = getNamespacedMeta();
        var projName = System.getenv(Environment.CI_PROJECT_NAME.toString());
        meta.setName(Util.hash(getNamespaceString() + autoCD.getRegistryImagePath() + projName));
        var labels = Map.of("k8s-app", getK8sApp(autoCD));
        meta.setLabels(labels);

        var spec = new ExtensionsV1beta1DeploymentSpec();
        spec.setReplicas(1);
        var select = new V1LabelSelector();
        select.setMatchLabels(labels);
        spec.setSelector(select);

        var template = new V1PodTemplateSpec();
        spec.setTemplate(template);
        var templateMeta = new V1ObjectMeta();
        template.setMetadata(templateMeta);

        templateMeta.setLabels(Map.of(
                "k8s-app", getK8sApp(autoCD),
                "name", getName()));
        template.setMetadata(templateMeta);

        var podSpec = new V1PodSpec();
        template.setSpec(podSpec);

        podSpec.setTerminationGracePeriodSeconds(autoCD.getTerminationGracePeriod());

        if (!autoCD.getVolumes().isEmpty()) {
            podSpec.setVolumes(autoCD.getVolumes().stream().map(volume -> {
                var vol = new V1Volume();
                vol.setName(getPVCName(volume, autoCD));
                var source = new V1PersistentVolumeClaimVolumeSource();
                source.setClaimName(getPVCName(volume, autoCD));
                vol.setPersistentVolumeClaim(source);
                return vol;

            }).collect(Collectors.toList()));
        }

        var port = new V1ContainerPort();

        if (finder.getFileType().equals(FileType.VUE)) {
            port.setContainerPort(autoCD.getContainerPort());
        } else {
            port.setContainerPort(autoCD.getContainerPort());
        }

        port.setName("http");

        var containerBuilder = new V1ContainerBuilder()
                .withImage(autoCD.getRegistryImagePath())
                .withName(getName() + "-c")
                .withPorts(port)
                .withArgs(autoCD.getArgs());

        var neededInitContainer = new ArrayList<V1Container>();
        if (!autoCD.getVolumes().isEmpty()) {
            var volumes = autoCD.getVolumes().stream().map(volume -> {
                var vol = new V1VolumeMount();
                vol.setName(getPVCName(volume, autoCD));
                vol.setMountPath(volume.getVolumeMount());

                if (volume.getFilePermission() != null) {
                    neededInitContainer.add(getVolumePermissionConditioner(volume.getFilePermission(), vol));
                }

                return vol;
            }).collect(Collectors.toList());

            if (!neededInitContainer.isEmpty()) {
                podSpec.setInitContainers(neededInitContainer);
            }

            containerBuilder = containerBuilder.withVolumeMounts(volumes);
        }

        var container = containerBuilder.build();

        podSpec.setContainers(List.of(container));

        var secret = new V1LocalObjectReference();
        secret.setName("gitlab-bot");
        podSpec.setImagePullSecrets(List.of(secret));

        var dep = new ExtensionsV1beta1Deployment();
        dep.setMetadata(meta);
        dep.setSpec(spec);
        dep.setKind("Deployment");
        dep.setApiVersion(getApiVersion());
        return dep;
    }

    private V1Container getVolumePermissionConditioner(String perm, V1VolumeMount vol) {
        return new V1ContainerBuilder()
                .withImage("busybox")
                .withName("busybox" + "-c")
                .withCommand("/bin/chmod", "-R", perm, "/data")
                .withVolumeMounts(new V1VolumeMountBuilder()
                        .withName(vol.getName())
                        .withMountPath("/data")
                        .build()
                ).build();
    }

    @NotNull
    @Contract(pure = true)
    private String getApiVersion() {
        return "extensions/v1beta1";
    }

    private String getName() {
        if (isLocal()) {
            return "local-default-name";
        }

        return System.getenv(Environment.CI_PROJECT_NAME.toString());
    }


    @NotNull
    private V1ObjectMeta getNamespacedMeta() {
        var metadata = new V1ObjectMeta();
        metadata.setNamespace(getNamespaceString());
        return metadata;
    }

    private String getNamespaceString() {
        var nameSpaceName = "local-default";

        if (!isLocal()) {
            nameSpaceName = System.getenv(Environment.CI_PROJECT_NAMESPACE.toString());
        }

        return nameSpaceName;
    }

    @NotNull
    private V1Namespace getNamespace() {
        var ns = new V1Namespace();
        var metadata = new V1ObjectMeta();
        metadata.setName(getNamespaceString());

        ns.setMetadata(metadata);
        return ns;
    }

    public void removeFromK8s(AutoCD autoCD) {
        var ingress = getIngress(autoCD);
        deleteIngress(ingress);
        var service = getService(autoCD);
        deleteService(service);
        var deployment = getDeployment(autoCD);
        deleteDeployment(deployment);
        var claims = getPersistentVolumeClaims(autoCD);
        deleteClaims(claims);
    }

    private <T> void retry(T obj, @NotNull Consumer<T> function, @NotNull ApiException e) {
        if (e.getMessage().equals("Conflict")) {
            var resp = new Gson().fromJson(e.getResponseBody(), KubeStatusResponse.class);
            if (resp.getMessage().startsWith("object is being deleted")) {
                try {
                    log.info("Object is still being deleted, retrying...");
                    Thread.sleep(4000);
                    function.accept(obj);
                    return;
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }

            if (resp.getMessage().contains("already exists")) {
                return;
            }
        }


        log.error("Unknown error", e);
        log.info(e.getResponseBody());
    }
}
