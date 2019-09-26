package de.worldiety.autocd.persistence;

import java.util.Map;
import org.jetbrains.annotations.Contract;

public class AutoCD {
    private int containerPort = 8080;
    private int servicePort = 80;
    private boolean publiclyAccessible = true;
    private long terminationGracePeriod = 60L;
    private String dockerImagePath;
    private String volumeMount;
    private String registryImagePath;
    private String subdomain;
    private boolean shouldHost = true;
    private Map<String, String> environmentVariables;

    @Contract(pure = true)
    public AutoCD(int containerPort, int servicePort, boolean publiclyAccessible, String dockerImagePath, String volumeMount, String registryImagePath, String subdomain, boolean shouldHost, Map<String, String> environmentVariables) {
        this.containerPort = containerPort;
        this.servicePort = servicePort;
        this.publiclyAccessible = publiclyAccessible;
        this.dockerImagePath = dockerImagePath;
        this.volumeMount = volumeMount;
        this.registryImagePath = registryImagePath;
        this.subdomain = subdomain;
        this.shouldHost = shouldHost;
        this.environmentVariables = environmentVariables;
    }

    @Contract(pure = true)
    public AutoCD() {
    }

    public long getTerminationGracePeriod() {
        return terminationGracePeriod;
    }

    public void setTerminationGracePeriod(long terminationGracePeriod) {
        this.terminationGracePeriod = terminationGracePeriod;
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

    public Map<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }

    public void setEnvironmentVariables(Map<String, String> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public void setSubdomain(String subdomain) {
        this.subdomain = subdomain;
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

    public String getVolumeMount() {
        return volumeMount;
    }

    public void setVolumeMount(String volumeMount) {
        this.volumeMount = volumeMount;
    }
}
