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
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.NetworkingV1beta1Api;
import io.kubernetes.client.openapi.models.NetworkingV1beta1HTTPIngressPathBuilder;
import io.kubernetes.client.openapi.models.NetworkingV1beta1HTTPIngressRuleValueBuilder;
import io.kubernetes.client.openapi.models.NetworkingV1beta1Ingress;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressBackendBuilder;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressRuleBuilder;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressSpecBuilder;
import io.kubernetes.client.openapi.models.NetworkingV1beta1IngressTLSBuilder;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerBuilder;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentSpec;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1EnvVarBuilder;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1LimitRange;
import io.kubernetes.client.openapi.models.V1LimitRangeItem;
import io.kubernetes.client.openapi.models.V1LimitRangeSpec;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpecBuilder;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1PersistentVolumeList;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirementsBuilder;
import io.kubernetes.client.openapi.models.V1SecretBuilder;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.openapi.models.V1StatefulSetSpec;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeBuilder;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.openapi.models.V1VolumeMountBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class K8sClient {
    private static final Logger log = LoggerFactory.getLogger(K8sClient.class);
    //This needs to be set on delete Operations that should propagate to the related objects
    private static final String FOREGROUND = "Foreground";
    private final CoreV1Api api;
    private final DockerfileHandler finder;
    private final String hyphenedBuildType;
    private final String rawBuildType;
    private final CoreV1Api patchApi;
    private final String dockerCredentials;

    @Contract(pure = true)
    public K8sClient(CoreV1Api api, DockerfileHandler finder, String hyphenedBuildType, CoreV1Api patchApi, String dockerCredentials) {
        this.api = api;
        this.finder = finder;
        this.hyphenedBuildType = "-" + hyphenedBuildType;
        this.rawBuildType = hyphenedBuildType;
        this.patchApi = patchApi;
        this.dockerCredentials = dockerCredentials;
    }

    /**
     * Deploys the given configuration the the Kubernetes Cluster
     *
     * @param autoCD configuration
     */
    public void deployToK8s(AutoCD autoCD) {
        if (autoCD.getReplicas() > 1 && autoCD.getVolumes().size() != 0) {
            this.removeDeploymentFromK8s(autoCD);
            log.info("Deploying statefulset");
            deployStateful(autoCD);
        } else {
            log.info("Deploying deployment");
            deploy(autoCD);
        }
    }

    private void deployStateful(AutoCD autoCD) {
        var ingress = getIngress(autoCD);
        ingress.forEach(this::deleteIngress);
        var service = getService(autoCD);
        deleteService(service);
        var set = getStatefulSet(autoCD);
        deleteStatefulSet(set);
        var nameSpace = getNamespace();

        createNamespace(nameSpace);
        createNamespacedLimitRange(getNamespaceString());
        addSecret();

        createStatefulSet(set);
        createService(service);
        ingress.forEach(this::createIngress);
    }

    private void createStatefulSet(V1StatefulSet set) {
        var appsV1Api = getAppsV1ApiClient();
        try {
            appsV1Api.createNamespacedStatefulSet(set.getMetadata().getNamespace(), set, "true", null, null);
        } catch (ApiException e) {
            retry(set, this::createStatefulSet, e);
        }
    }

    private AppsV1Api getAppsV1ApiClient() {
        var appsV1Api = new AppsV1Api();
        appsV1Api.setApiClient(api.getApiClient());
        return appsV1Api;
    }

    private void deleteStatefulSet(V1StatefulSet set) {
        var appsV1Api = new AppsV1Api();
        appsV1Api.setApiClient(api.getApiClient());
        try {
            appsV1Api.deleteNamespacedStatefulSet(set.getMetadata().getName(), set.getMetadata().getNamespace(), "true", null, null, null, null, null);
        } catch (ApiException e) {
            log.warn("Could not delete deployment", e);
        } catch (JsonSyntaxException e) {
            ignoreGoogleParsingError(e);
        }
    }

    private V1StatefulSet getStatefulSet(AutoCD autoCD) {
        var meta = getNamespacedMeta();
        var projName = System.getenv(Environment.CI_PROJECT_NAME.toString());
        meta.setName(Util.hash(getNamespaceString() + autoCD.getIdentifierRegistryImagePath() + projName).substring(0, 20));
        var labels = Map.of("k8s-app", getK8sApp(autoCD), "serviceName", getCleanServiceNameLabel(autoCD));
        meta.setLabels(labels);

        var spec = new V1StatefulSetSpec();
        spec.setReplicas(autoCD.getReplicas());
        var select = new V1LabelSelector();
        select.setMatchLabels(labels);
        spec.setSelector(select);

        var template = new V1PodTemplateSpec();
        spec.setTemplate(template);
        var templateMeta = new V1ObjectMeta();
        template.setMetadata(templateMeta);

        templateMeta.setLabels(Map.of(
                "k8s-app", getK8sApp(autoCD),
                "name", getName(),
                "serviceName", getCleanServiceNameLabel(autoCD)));
        template.setMetadata(templateMeta);

        var podSpec = new V1PodSpec();
        podSpec.getSecurityContext().setRunAsNonRoot(true);
        template.setSpec(podSpec);

        podSpec.setTerminationGracePeriodSeconds(autoCD.getTerminationGracePeriod());

        spec.setTemplate(template);

        for (Volume volume : autoCD.getVolumes()) {
            var volumeName = getVolumeName(volume, autoCD);

            if (volume.isRetainVolume()) {
                var volTemp = new V1PersistentVolumeClaim();
                volTemp.setMetadata(new V1ObjectMetaBuilder().withName(volumeName).build());
                V1PersistentVolumeClaimSpec volumeClaimSpec = getV1PersistentVolumeClaimSpec(volume);
                volTemp.setSpec(volumeClaimSpec);
                spec.addVolumeClaimTemplatesItem(volTemp);
            } else {
                // set empty dirs: https://kubernetes.io/docs/concepts/storage/volumes/#emptydir
                var builder = new V1VolumeBuilder();
                builder.withName(volumeName);
                builder = builder.withNewEmptyDir().endEmptyDir();
                podSpec.addVolumesItem(builder.build());
            }
        }

        var containerBuilder = getV1ContainerBuilder(autoCD);

        var neededInitContainer = new ArrayList<V1Container>();
        if (!autoCD.getVolumes().isEmpty()) {
            var volumes = autoCD.getVolumes().stream().map(volume -> {
                var vol = new V1VolumeMount();
                vol.setName(getVolumeName(volume, autoCD));
                vol.setMountPath(volume.getVolumeMount());

                if (volume.getFolderPermission() != null) {
                    neededInitContainer.add(getVolumePermissionConditioner(volume.getFolderPermission(), vol));
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


        var dep = new V1StatefulSet();
        dep.setMetadata(meta);
        dep.setSpec(spec);
        dep.setKind("StatefulSet");
        dep.setApiVersion(getApiVersionAppsV1());

        return dep;
    }

    @NotNull
    private V1ContainerPort getV1ContainerPort(AutoCD autoCD) {
        var port = new V1ContainerPort();

        if (finder.getFileType().equals(FileType.VUE)) {
            port.setContainerPort(autoCD.getContainerPort());
        } else {
            port.setContainerPort(autoCD.getContainerPort());
        }

        port.setName("http");
        return port;
    }

    private V1PersistentVolumeClaimSpec getV1PersistentVolumeClaimSpec(@NotNull Volume vol) {
        return new V1PersistentVolumeClaimSpecBuilder()
                .withAccessModes("ReadWriteOnce")
                .withResources(new V1ResourceRequirementsBuilder()
                        .withRequests(Map.of("storage", new Quantity(vol.getVolumeSize())))
                        .build())
                .withStorageClassName("do-block-storage")
                .build();
    }


    @SuppressWarnings("DuplicatedCode")
    private void deploy(AutoCD autoCD) {
        var ingress = getIngress(autoCD);
        ingress.forEach(this::deleteIngress);
        var service = getService(autoCD);
        deleteService(service);
        var claims = getPersistentVolumeClaims(autoCD);
        var pvs = protectPVS(autoCD, claims);
        log.info(pvs.toString());
        unprotectPVS(autoCD);
        var deployment = getDeployment(autoCD);
        deleteDeployment(deployment);
        deleteClaims(claims);
        var nameSpace = getNamespace();
        cleanupPVC(nameSpace.getMetadata().getName(), claims);

        createNamespace(nameSpace);
        createNamespacedLimitRange(getNamespaceString());
        addSecret();

        createClaims(claims);
        createDeployment(deployment);
        createService(service);

        reclaimPVS(pvs);

        if (autoCD.isPubliclyAccessible()) {
            ingress.forEach(this::createIngress);
        }
    }

    /**
     * Adds the image pull secret to the namespace
     */
    private void addSecret() {
        var secret = new V1SecretBuilder().addToStringData(".dockerconfigjson", dockerCredentials)
                .withKind("Secret")
                .withMetadata(getNamedNamespacedMeta("gitlab-bot"))
                .withType("kubernetes.io/dockerconfigjson")
                .withApiVersion(getApiVersionV1())
                .build();

        try {
            api.createNamespacedSecret(secret.getMetadata().getNamespace(), secret, "true", null, null);
        } catch (ApiException e) {
            log.error("Could not create secret", e);
        }
    }

    @SuppressWarnings("unused")
    private void deleteNamespace(@NotNull V1Namespace namespace) {
        try {
            api.deleteNamespace(namespace.getMetadata().getName(), "true", null, null, null, null, null);
        } catch (ApiException e) {
            checkApiError(e, "namespace");
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

    private void applyDeleteClaim(@NotNull V1PersistentVolumeClaim claim) {
        try {
            api.deleteNamespacedPersistentVolumeClaim(claim.getMetadata().getName(), claim.getMetadata().getNamespace(), null, null, null, null, null, null);
            log.info("Deleted claim: " + claim.getMetadata().getName());
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

    private void deleteDeployment(@NotNull V1Deployment deployment) {
        var v1Api = getAppsV1Api();
        try {
            v1Api.deleteNamespacedDeployment(deployment.getMetadata().getName(), deployment.getMetadata().getNamespace(), "true", null, null, null, null, null);
        } catch (ApiException e) {
            checkApiError(e, "deployment");
        } catch (JsonSyntaxException e) {
            ignoreGoogleParsingError(e);
        }
    }

    private void deleteService(@NotNull V1Service service) {
        try {
            api.deleteNamespacedService(service.getMetadata().getName(), service.getMetadata().getNamespace(), null, null, null, null, null, null);
        } catch (ApiException e) {
            checkApiError(e, "service");
        } catch (JsonSyntaxException e) {
            ignoreGoogleParsingError(e);
        }
    }

    private void deleteIngress(@NotNull NetworkingV1beta1Ingress ingress) {
        var extensionsV1beta1Api = getNetworkingV1beta1Api();
        try {
            extensionsV1beta1Api.deleteNamespacedIngress(ingress.getMetadata().getName(), ingress.getMetadata().getNamespace(), null, null, null, null, null, null);
        } catch (ApiException e) {
            checkApiError(e, "ingress");
        } catch (JsonSyntaxException e) {
            ignoreGoogleParsingError(e);
        }
    }

    /**
     * This needs to be caught since kubernetes sometimes returns a status OR the object, which results in a json parsing
     * error.
     * <p>
     * https://github.com/kubernetes-client/java/issues/86
     *
     * @param ignored ignored
     */
    @SuppressWarnings("unused")
    private void ignoreGoogleParsingError(JsonSyntaxException ignored) {
        //No-op
    }

    private void checkApiError(ApiException e, String name) {
        if (!e.getMessage().contains("Not found")) {
            log.error("Could not delete " + name, e);
        }
    }

    /**
     * This method is used to remove the k8s reference of the "old" pod so it gets marked as "Available" again
     *
     * @param pvs names of the volumes to patch
     */
    private void reclaimPVS(@NotNull List<String> pvs) {
        pvs.forEach(pv -> {
            V1Patch reclaimPatch = new V1Patch("[{\"op\":\"remove\",\"path\":\"/spec/claimRef\"}]");
            try {
                patchApi.patchPersistentVolume(pv, reclaimPatch, null, null, null, null);
            } catch (ApiException e) {
                log.error("Could not reclaim PV", e);
                throw new IllegalStateException(e);
            }
        });
    }

    /**
     * This method removes the "Retain" protection that was added earlier if the volume has been set to:
     * retainVolume = false
     *
     * @param autoCD configuration
     */
    private void unprotectPVS(AutoCD autoCD) {
        V1PersistentVolumeList pvs;
        try {
            pvs = api.listPersistentVolume(null, null, null, null, null, null, null, null, null);
            List<String> namesToProtect = getNamesToProtect(autoCD);

            pvs.getItems().forEach(pv -> {
                if (pv.getSpec() != null && pv.getSpec().getClaimRef() != null) {
                    var name = pv.getSpec().getClaimRef().getName();
                    if (namesToProtect.contains(name)) {
                        return;
                    }

                    V1Patch deletePatch = new V1Patch("[{\"op\":\"replace\",\"path\":\"/spec/persistentVolumeReclaimPolicy\",\"value\":\"Delete\"}]");

                    applyPatchToPVS(pv, deletePatch);
                }
            });

        } catch (ApiException e) {
            log.error("Could not perform PV protection: ", e);
        }
    }

    @NotNull
    private List<String> getNamesToProtect(@NotNull AutoCD autoCD) {
        return autoCD.getVolumes()
                .stream()
                .filter(Volume::isRetainVolume)
                .map(it -> getPVCName(it, autoCD))
                .collect(Collectors.toList());
    }

    private void applyPatchToPVS(@NotNull V1PersistentVolume pv, V1Patch patch) {
        try {
            patchApi.patchPersistentVolume(pv.getMetadata().getName(), patch, null, null, null, null);
        } catch (ApiException e) {
            log.error("Could not patch PV", e);
            throw new IllegalStateException(e);
        }
    }

    /**
     * This methods adds the "Retain" policy to all volumes that have been configured via autoCD to retain their data.
     * This is done so k8s doesn't propagate the deletion of the deployment to these Persistent Volumes. It also hardwires
     * the name of the PersistentVolume into the PersistentVolumeClaim so it will try and grab the old one and not provision
     * a new one
     *
     * @param autoCD configuration
     * @param claims all pvc's generated from the autoCD
     * @return the names of the Protected volumes to process later on
     */
    @NotNull
    private List<String> protectPVS(AutoCD autoCD, List<V1PersistentVolumeClaim> claims) {
        V1PersistentVolumeList pvs;
        var strings = new ArrayList<String>();
        try {
            pvs = api.listPersistentVolume(null, null, null, null, null, null, null, null, null);
            List<String> namesToProtect = getNamesToProtect(autoCD);

            pvs.getItems().forEach(pv -> {
                if (pv.getSpec() != null && pv.getSpec().getClaimRef() != null) {
                    var name = pv.getSpec().getClaimRef().getName();
                    if (!namesToProtect.contains(name)) {
                        return;
                    }

                    strings.add(pv.getMetadata().getName());

                    V1Patch retainPatch = new V1Patch("[{\"op\":\"replace\",\"path\":\"/spec/persistentVolumeReclaimPolicy\",\"value\":\"Retain\"}]");

                    applyPatchToPVS(pv, retainPatch);

                    claims.stream().filter(it -> it.getMetadata().getName().equals(name)).forEach(it -> {
                        var spec = it.getSpec();
                        spec.setVolumeName(pv.getMetadata().getName());
                    });
                }

            });

        } catch (ApiException e) {
            log.error("Could not perform PV protection: ", e);
        }

        return strings;
    }

    /**
     * This method clears any "dangling" persistent volume claims that are not bound to any pod, this may occur if
     * the user deletes a volume from the configuration and therefore it can no longer be found since the name is unknown
     *
     * @param namespace The namespace
     * @param claims    all claims to filter the correct ones
     */
    private void cleanupPVC(String namespace, List<V1PersistentVolumeClaim> claims) {
        try {
            var pvcs = api.listNamespacedPersistentVolumeClaim(namespace, "true", null, null, null, null, null, null, null, null);
            var pods = api.listNamespacedPod(namespace, "true", null, null, null, null, null, null, null, null);
            var validPVCNames = pods.getItems()
                    .stream()
                    .filter(pod -> pod.getSpec().getVolumes().size() > 0)
                    .map(it -> it.getSpec().getVolumes())
                    .flatMap(Collection::stream)
                    .map(V1Volume::getName)
                    .collect(Collectors.toList());

            pvcs.getItems().stream()
                    .filter(it -> !validPVCNames.contains(it.getMetadata().getName()))
                    .filter(it -> claims.stream().noneMatch(claim -> claim.getMetadata().getName().equals(it.getMetadata().getName())))
                    .forEach(this::applyDeleteClaim);

        } catch (ApiException e) {
            log.error("Could not perform PVC cleanup: ", e);
        }
    }

    private void createIngress(NetworkingV1beta1Ingress ingress) {
        var extensionsV1beta1Api = getNetworkingV1beta1Api();
        try {
            extensionsV1beta1Api.createNamespacedIngress(ingress.getMetadata().getNamespace(), ingress, "true", null, null);
        } catch (ApiException e) {
            retry(ingress, this::createIngress, e);
        }
    }

    @NotNull
    private AppsV1Api getAppsV1Api() {
        var apiv1 = new AppsV1Api();
        apiv1.setApiClient(api.getApiClient());
        return apiv1;
    }

    @NotNull
    private NetworkingV1beta1Api getNetworkingV1beta1Api() {
        var extensionsV1beta1Api = new NetworkingV1beta1Api();
        extensionsV1beta1Api.setApiClient(api.getApiClient());
        return extensionsV1beta1Api;
    }

    private void createService(V1Service service) {
        try {
            api.createNamespacedService(service.getMetadata().getNamespace(), service, "true", null, null);
        } catch (ApiException e) {
            retry(service, this::createService, e);
        }
    }

    private void createDeployment(V1Deployment deployment) {
        var v1api = getAppsV1Api();
        try {
            v1api.createNamespacedDeployment(deployment.getMetadata().getNamespace(), deployment, "true", null, null);
        } catch (ApiException e) {
            retry(deployment, this::createDeployment, e);
        }
    }

    private void createNamespace(V1Namespace nameSpace) {
        try {
            api.createNamespace(nameSpace, "true", null, null);
        } catch (ApiException e) {
            retry(nameSpace, this::createNamespace, e);
        }
    }

    private void createNamespacedLimitRange(String nameSpaceString) {
        try {
            // we do not provide a AutoCD config here, because the LimitRange should be selected by the project FileType
            api.createNamespacedLimitRange(nameSpaceString, generateV1LimitRange(null), "true", null, null);
        } catch (ApiException e) {
            retry(nameSpaceString, this::createNamespacedLimitRange, e);
        }
    }

    @NotNull
    private String getPVCName(Volume volume, @NotNull AutoCD autoCD) {
        var str = getNamespaceString() + "-" + getName() + "-" + autoCD.getIdentifierRegistryImagePath() + "-" + autoCD.getVolumes().indexOf(volume) + "-claim";
        return hash(str).substring(0, 20);
    }

    @NotNull
    private String getVolumeName(Volume volume, @NotNull AutoCD autoCD) {
        return getPVCName(volume, autoCD);
    }

    private List<V1PersistentVolumeClaim> getPersistentVolumeClaims(@NotNull AutoCD autoCD) {
        return autoCD.getVolumes().stream().map(volume -> {
            var pvc = new V1PersistentVolumeClaim();
            pvc.setKind("PersistentVolumeClaim");
            var meta = getNamespacedMeta();
            meta.setName(getPVCName(volume, autoCD));
            pvc.setMetadata(meta);
            V1PersistentVolumeClaimSpec spec = getV1PersistentVolumeClaimSpec(volume);

            pvc.setSpec(spec);

            return pvc;
        }).collect(Collectors.toList());
    }

    private List<NetworkingV1beta1Ingress> getIngress(@NotNull AutoCD autoCD) {
        var extensionsV1beta1Api = getNetworkingV1beta1Api();
        try {
            var ingresses = extensionsV1beta1Api.listIngressForAllNamespaces(null, null, null, null, null, null, null, null, null);
            var ingressWithHostAlreadyPresent = ingresses.getItems()
                    .stream()
                    .filter(it -> !it.getMetadata().getNamespace().equals(getNamespaceString()))
                    .anyMatch(it ->
                            it.getSpec().getRules().stream()
                                    .filter(rule -> rule != null && rule.getHost() != null)
                                    .anyMatch(rule -> autoCD.getSubdomains().contains(rule.getHost())));

            if (ingressWithHostAlreadyPresent) {
                throw new IllegalStateException("There is already an ingress with host: " + autoCD.getSubdomains() + " present");
            }

        } catch (ApiException e) {
            log.error("Could not get Ingresses for all namespaces", e);
        }

        List<NetworkingV1beta1Ingress> returnList = new ArrayList<>();

        for (String subdomain : autoCD.getSubdomains()) {

            var ingress = new NetworkingV1beta1Ingress();
            ingress.setKind("Ingress");
            var meta = getNamespacedMeta();
            meta.setName(Util.hash(subdomain + getNamespaceString() + "-" + getName() + "-ingress" + autoCD.getIdentifierRegistryImagePath()).substring(0, 20));
            meta.setAnnotations(Map.of("cert-manager.io/cluster-issuer", "letsencrypt-prod",
                    "kubernetes.io/ingress.class", "nginx", "nginx.ingress.kubernetes.io/proxy-body-size", "1024m"));

            var rules = new NetworkingV1beta1IngressRuleBuilder();

            rules.withHost(subdomain)
                    .withHttp(new NetworkingV1beta1HTTPIngressRuleValueBuilder()
                            .withPaths(new NetworkingV1beta1HTTPIngressPathBuilder().withPath("/")
                                    .withBackend(new NetworkingV1beta1IngressBackendBuilder()
                                            .withServiceName(getServiceName(autoCD))
                                            .withServicePort(new IntOrString(autoCD.getServicePort()))
                                            .build())
                                    .build())
                            .build());

            var spec = new NetworkingV1beta1IngressSpecBuilder()
                    .withRules(rules.build())
                    .withTls(new NetworkingV1beta1IngressTLSBuilder()
                            .withHosts(subdomain)
                            .withSecretName(Util.hash(subdomain).substring(0, 10))
                            .build())
                    .build();


            ingress.setSpec(spec);
            ingress.setMetadata(meta);

            returnList.add(ingress);
        }


        return returnList;
    }


    private String getCleanServiceNameLabel(AutoCD autoCD) {
        var unclean = getServiceNameLabel(autoCD);
        var clean = unclean.replaceAll("/", "-").replaceAll(":", "");
        if (clean.startsWith("-")) {
            clean = clean.substring(1);
        }

        return clean;
    }

    private String getServiceNameLabel(AutoCD autoCD) {
        if (autoCD.getServiceName() != null) {
            return autoCD.getServiceName();
        }

        if (autoCD.getRegistryImagePath() != null) {
            return autoCD.getRegistryImagePath().replaceAll("registry\\.worldiety\\.net", "");
        }

        return System.getenv(Environment.CI_PROJECT_NAME.toString());
    }

    @NotNull
    private String getServiceName(@NotNull AutoCD autoCD) {
        if (autoCD.getServiceName() != null) {
            return autoCD.getServiceName();
        }

        return "service-" + Util.hash(getNamespaceString() + "-" + getName() + "-service").substring(0, 20);
    }

    @NotNull
    private V1Service getService(@NotNull AutoCD autoCD) {
        var service = new V1Service();
        service.setKind("Service");
        var meta = getNamespacedMeta();
        meta.setName(getServiceName(autoCD));
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

    @NotNull
    private V1Deployment getDeployment(@NotNull AutoCD autoCD) {
        var meta = getNamespacedMeta();
        var projName = System.getenv(Environment.CI_PROJECT_NAME.toString());
        meta.setName(Util.hash(getNamespaceString() + autoCD.getIdentifierRegistryImagePath() + projName));
        var labels = Map.of("k8s-app", getK8sApp(autoCD));
        meta.setLabels(labels);

        var spec = new V1DeploymentSpec();
        spec.setReplicas(autoCD.getReplicas());
        var select = new V1LabelSelector();
        select.setMatchLabels(labels);
        spec.setSelector(select);

        var template = new V1PodTemplateSpec();
        spec.setTemplate(template);
        var templateMeta = new V1ObjectMeta();
        template.setMetadata(templateMeta);

        templateMeta.setLabels(Map.of(
                "k8s-app", getK8sApp(autoCD),
                "name", getName(),
                "serviceName", getCleanServiceNameLabel(autoCD)));
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

        // set the resources and limits
        var resourceBuilder = getV1ContainerBuilder(autoCD).withNewResources();
        V1LimitRangeItem limitRangeItem = generateV1LimitRange(autoCD).getSpec().getLimits().get(0);
        resourceBuilder = resourceBuilder.addToLimits(limitRangeItem.getDefault());
        resourceBuilder = resourceBuilder.addToRequests(limitRangeItem.getDefaultRequest());

        // create container builder
        V1ContainerBuilder containerBuilder = resourceBuilder.endResources();

        var neededInitContainer = new ArrayList<V1Container>();
        if (!autoCD.getVolumes().isEmpty()) {
            var volumes = autoCD.getVolumes().stream().map(volume -> {
                var vol = new V1VolumeMount();
                vol.setName(getPVCName(volume, autoCD));
                vol.setMountPath(volume.getVolumeMount());

                if (volume.getFolderPermission() != null) {
                    neededInitContainer.add(getVolumePermissionConditioner(volume.getFolderPermission(), vol));
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

        var dep = new V1Deployment();
        dep.setMetadata(meta);
        dep.setSpec(spec);
        dep.setKind("Deployment");
        dep.setApiVersion("apps/v1");
        return dep;
    }

    private V1ContainerBuilder getV1ContainerBuilder(@NotNull AutoCD autoCD) {
        final V1ContainerPort port = getV1ContainerPort(autoCD);

        List<V1EnvVar> variables = new ArrayList<>();
        if (autoCD.getEnvironmentVariables() != null) {
            var type = autoCD.getEnvironmentVariables().get(rawBuildType);
            if (type != null) {
                variables = type.entrySet()
                        .stream()
                        .map(entry -> new V1EnvVarBuilder().withName(entry.getKey()).withValue(entry.getValue()).build())
                        .collect(Collectors.toList());
            }
        }

        // set security fixes
        var securityContextBuilder = new V1ContainerBuilder().editOrNewSecurityContext();
        //securityContextBuilder = securityContextBuilder.withAllowPrivilegeEscalation(false);
        //securityContextBuilder = securityContextBuilder.withPrivileged(false);
        //securityContextBuilder = securityContextBuilder.withRunAsUser(10123L);
        //securityContextBuilder = securityContextBuilder.withRunAsGroup(10123L);
        //securityContextBuilder = securityContextBuilder.withReadOnlyRootFilesystem(true);

        return securityContextBuilder.endSecurityContext()
                .withImage(autoCD.getRegistryImagePath())
                .withName(getName() + "-c")
                .withPorts(port)
                .withEnv(variables)
                .withArgs(autoCD.getArgs())
                .withImagePullPolicy("Always");
    }

    @NotNull
    private String getK8sApp(@NotNull AutoCD autoCD) {
        return Util.hash(getNamespaceString() + "-" + getName() + "-" + Util.hash(autoCD.getIdentifierRegistryImagePath())).substring(0, 20) + hyphenedBuildType;
    }

    /**
     * This method returns an init container that may be required to set the read/write/execute flags (as numbers) on
     * a mount. This may be required for redis or mysql for example because those containers run as USER and the volumes
     * only have read/write/execute as ROOT. This is needed because the DigitalOcean Spec doesn't implement setting these
     * via configuration as per the docs: https://www.digitalocean.com/docs/kubernetes/how-to/add-volumes/#setting-permissions-on-volumes
     *
     * @param perm Unix Permissions
     * @param vol  the volumeMount to condition
     * @return the initContainer used to condition the Volume
     */
    private V1Container getVolumePermissionConditioner(String perm, @NotNull V1VolumeMount vol) {
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
    private String getApiVersionAppsV1() {
        return "apps/v1";
    }

    @NotNull
    @Contract(pure = true)
    private String getApiVersionV1() {
        return "v1";
    }

    @NotNull
    private String getName() {
        if (isLocal()) {
            return "local-default-name";
        }

        return System.getenv(Environment.CI_PROJECT_NAME.toString()).replace("_", "-") + hyphenedBuildType;
    }

    @NotNull
    private V1ObjectMeta getNamespacedMeta() {
        var metadata = new V1ObjectMeta();
        metadata.setNamespace(getNamespaceString());
        return metadata;
    }

    private V1ObjectMeta getNamedNamespacedMeta(String name) {
        var metadata = getNamespacedMeta();
        metadata.setName(name);
        return metadata;
    }

    @NotNull
    private String getNamespaceString() {
        var nameSpaceName = "local-default";

        if (!isLocal()) {
            nameSpaceName = System.getenv(Environment.CI_PROJECT_NAMESPACE.toString());
            nameSpaceName = nameSpaceName.replaceAll("/", "-");
        }

        return nameSpaceName + hyphenedBuildType;
    }

    @NotNull
    private V1Namespace getNamespace() {
        var ns = new V1Namespace();
        var metadata = new V1ObjectMeta();
        metadata.setName(getNamespaceString());

        ns.setMetadata(metadata);
        return ns;
    }

    // this code is duplicated because of our checkstyle configuration...
    @SuppressWarnings("DuplicatedCode")
    public void removeDeploymentFromK8s(AutoCD autoCD) {
        var ingress = getIngress(autoCD);
        ingress.forEach(this::deleteIngress);
        var service = getService(autoCD);
        deleteService(service);
        var deployment = getDeployment(autoCD);
        deleteDeployment(deployment);
        var claims = getPersistentVolumeClaims(autoCD);
        deleteClaims(claims);
    }

    /**
     * This method will retry a given function if the resource is still in the "Terminating" phase. We need to catch this exception
     * because the parsing of JSON responses is buggy: https://github.com/kubernetes-client/java/issues/86
     *
     * @param obj      The object that will be passed to the function
     * @param function the function that will be applied after sleeping
     * @param e        the caught exception
     * @param <T>      Any Kubernetes Object
     */
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

    /**
     * Generate a {@link V1LimitRange} from a {@link AutoCD} config.
     * If no config is provided, we use the default values for the projects {@link FileType}.
     *
     * @param autoCD
     * @return
     */
    private V1LimitRange generateV1LimitRange(@Nullable AutoCD autoCD) {
        V1LimitRange v1LimitRange = new V1LimitRange();
        // set metadata
        V1ObjectMeta v1ObjectMeta = new V1ObjectMeta();
        v1ObjectMeta.setName(getNamespaceString() + "-limitrange");
        //v1ObjectMeta.setLabels(Map.of("type", "");
        v1LimitRange.setMetadata(v1ObjectMeta);

        // set item
        V1LimitRangeItem v1LimitRangeItem = new V1LimitRangeItem();
        v1LimitRangeItem.setType("Container");

        AutoCD.Resources resources;
        if (autoCD != null && autoCD.getResources() != null) {
            resources = autoCD.getResources();
        } else {
            resources = AutoCD.getDefaultLimitRangeFor(finder.getFileType());
        }

        Map<String, Quantity> defaultLimits = new HashMap<>();
        if (resources.getLimits() != null && resources.getLimits().getCPU() != null) {
            defaultLimits.put("cpu", Quantity.fromString(resources.getLimits().getCPU()));
        }
        if (resources.getLimits() != null && resources.getLimits().getMemory() != null) {
            defaultLimits.put("memory", Quantity.fromString(resources.getLimits().getMemory()));
        }
        v1LimitRangeItem.setDefault(defaultLimits);

        Map<String, Quantity> defaultRequests = new HashMap<>();
        if (resources.getRequests() != null && resources.getRequests().getCPU() != null) {
            defaultRequests.put("cpu", Quantity.fromString(resources.getRequests().getCPU()));
        }
        if (resources.getRequests() != null && resources.getRequests().getMemory() != null) {
            defaultRequests.put("memory", Quantity.fromString(resources.getRequests().getMemory()));
        }
        v1LimitRangeItem.setDefaultRequest(defaultRequests);

        // set spec
        V1LimitRangeSpec v1LimitRangeSpec = new V1LimitRangeSpec();
        v1LimitRangeSpec.addLimitsItem(v1LimitRangeItem);
        v1LimitRange.setSpec(v1LimitRangeSpec);

        return v1LimitRange;
    }
}
