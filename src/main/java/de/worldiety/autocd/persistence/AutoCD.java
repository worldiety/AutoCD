package de.worldiety.autocd.persistence;

import de.worldiety.autocd.util.FileType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AutoCD {
    private int containerPort = 8080;
    private int servicePort = 80;
    private int replicas = 1;
    private boolean publiclyAccessible = true;
    private long terminationGracePeriod = 60L;
    private String dockerImagePath;
    private String registryImagePath;
    private Map<String, List<String>> subdomainsEnv = new HashMap<>();
    private boolean shouldHost = true;
    private List<Volume> volumes = new ArrayList<>();
    private Map<String, Map<String, String>> environmentVariables = new HashMap<>();
    private List<AutoCD> otherImages = new ArrayList<>();
    private List<String> args = new ArrayList<>();
    private String serviceName = null;
    private List<String> subdomains = new ArrayList<>();
    private Resources resources = null;

    public AutoCD() {
    }

    public AutoCD(int containerPort, int servicePort, int replicas, boolean publiclyAccessible, long terminationGracePeriod, String dockerImagePath, String registryImagePath, Map<String, List<String>> subdomainsEnv, boolean shouldHost, List<Volume> volumes, Map<String, Map<String, String>> environmentVariables, List<AutoCD> otherImages, List<String> args, String serviceName, List<String> subdomains, Resources resources) {
        this.containerPort = containerPort;
        this.servicePort = servicePort;
        this.replicas = replicas;
        this.publiclyAccessible = publiclyAccessible;
        this.terminationGracePeriod = terminationGracePeriod;
        this.dockerImagePath = dockerImagePath;
        this.registryImagePath = registryImagePath;
        this.subdomainsEnv = subdomainsEnv;
        this.shouldHost = shouldHost;
        this.volumes = volumes;
        this.environmentVariables = environmentVariables;
        this.otherImages = otherImages;
        this.args = args;
        this.serviceName = serviceName;
        this.subdomains = subdomains;
        this.resources = resources;
    }

    /**
     * Get the default {@link AutoCD.Resources} for a given {@link FileType}.
     * <p>
     * We use the default values if no {@link FileType} or {@link FileType#OTHER} is provided.
     *
     * @param fileType The type of files in this project
     * @return Limits and requests for cpu and memory
     */
    public static @NotNull AutoCD.Resources getDefaultLimitRangeFor(@Nullable FileType fileType) {

        AutoCD.Resources defaultResources = new AutoCD.Resources("0.1", "0.01", "250Mi", "50Mi");
        if (fileType == null) {
            return defaultResources;
        }

        switch (fileType) {
            case JAVA:
                return new AutoCD.Resources("0.15", "0.05", "500Mi", "200Mi");
            case GO:
                return new AutoCD.Resources("0.1", "0.001", "50Mi", "5Mi");
            case VUE:
            case NUXT:
            case EISEN:
                return new AutoCD.Resources("0.1", "0.01", "70Mi", "15Mi");
            case SWIFT:
                return new AutoCD.Resources("0.1", "0.01", "150Mi", "75Mi");
            default:
                return defaultResources;
        }
    }

    public int getReplicas() {
        return replicas;
    }

    public void setReplicas(int replicas) {
        this.replicas = replicas;
    }

    public List<String> getArgs() {
        return args;
    }

    public void setArgs(List<String> args) {
        this.args = args;
    }

    public List<AutoCD> getOtherImages() {
        return otherImages;
    }

    public void setOtherImages(List<AutoCD> otherImages) {
        this.otherImages = otherImages;
    }

    public List<Volume> getVolumes() {
        return volumes;
    }

    public void setVolumes(List<Volume> volumes) {
        this.volumes = volumes;
    }

    public long getTerminationGracePeriod() {
        return terminationGracePeriod;
    }

    public void setTerminationGracePeriod(long terminationGracePeriod) {
        this.terminationGracePeriod = terminationGracePeriod;
    }

    public Map<String, List<String>> getSubdomainsEnv() {
        return subdomainsEnv;
    }

    public void setSubdomainsEnv(Map<String, List<String>> subdomainsEnv) {
        this.subdomainsEnv = subdomainsEnv;
    }

    public List<String> getSubdomains() {
        return subdomains;
    }

    public void setSubdomains(List<String> subdomains) {
        this.subdomains = subdomains;
    }

    public boolean isPubliclyAccessible() {
        return publiclyAccessible;
    }

    public void setPubliclyAccessible(boolean publiclyAccessible) {
        this.publiclyAccessible = publiclyAccessible;
    }

    public boolean isShouldHost() {
        return shouldHost;
    }

    public void setShouldHost(boolean shouldHost) {
        this.shouldHost = shouldHost;
    }

    public Map<String, Map<String, String>> getEnvironmentVariables() {
        return environmentVariables;
    }

    public void setEnvironmentVariables(Map<String, Map<String, String>> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    public int getServicePort() {
        return servicePort;
    }

    public void setServicePort(int servicePort) {
        this.servicePort = servicePort;
    }

    public String getRegistryImagePath() {
        return registryImagePath;
    }

    public void setRegistryImagePath(String registryImagePath) {
        this.registryImagePath = registryImagePath;
    }

    public String getIdentifierRegistryImagePath() {
        return registryImagePath.split(":")[0];
    }

    public int getContainerPort() {
        return containerPort;
    }

    public void setContainerPort(int containerPort) {
        this.containerPort = containerPort;
    }

    public String getDockerImagePath() {
        return dockerImagePath;
    }

    public void setDockerImagePath(String dockerImagePath) {
        this.dockerImagePath = dockerImagePath;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Resources getResources() {
        return resources;
    }

    public void setResources(Resources resources) {
        this.resources = resources;
    }

    public static class Resources {
        private ResourceValues limits;
        private ResourceValues requests;

        public Resources(String cpuLimit, String cpuRequest, String ramLimit, String ramRequest) {
            this.limits = new ResourceValues(cpuLimit, ramLimit);
            this.requests = new ResourceValues(cpuRequest, ramRequest);
        }

        public Resources(ResourceValues limits, ResourceValues requests) {
            this.limits = limits;
            this.requests = requests;
        }

        public Resources() {
        }

        public ResourceValues getLimits() {
            return limits;
        }

        public void setLimits(ResourceValues value) {
            this.limits = value;
        }

        public ResourceValues getRequests() {
            return requests;
        }

        public void setRequests(ResourceValues value) {
            this.requests = value;
        }

        public static class ResourceValues {
            private String cpu;
            private String memory;

            public ResourceValues(String cpu, String memory) {
                this.cpu = cpu;
                this.memory = memory;
            }

            public ResourceValues() {
            }

            public String getCPU() {
                return cpu;
            }

            public void setCPU(String value) {
                this.cpu = value;
            }

            public String getMemory() {
                return memory;
            }

            public void setMemory(String value) {
                this.memory = value;
            }
        }
    }
}
