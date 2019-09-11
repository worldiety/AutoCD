package de.worldiety.autocd.persistence;

import java.util.Map;
import org.jetbrains.annotations.Contract;

public class AutoCD {
    private int containerPort = 8080;
    private int servicePort = 80;
    private String imagePath;
    private String volumeMount;
    private String registryImagePath;
    private String subdomain;
    private Map<String, String> environmentVariables;

    @Contract(pure = true)
    public AutoCD(int containerPort, String imagePath, String volumeMount) {
        this.containerPort = containerPort;
        this.imagePath = imagePath;
        this.volumeMount = volumeMount;
    }

    @Contract(pure = true)
    public AutoCD() {
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

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getVolumeMount() {
        return volumeMount;
    }

    public void setVolumeMount(String volumeMount) {
        this.volumeMount = volumeMount;
    }
}
