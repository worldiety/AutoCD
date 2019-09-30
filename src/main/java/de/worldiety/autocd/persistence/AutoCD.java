package de.worldiety.autocd.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Contract;

public class AutoCD {
    private int containerPort = 8080;
    private int servicePort = 80;
    private boolean publiclyAccessible = true;
    private long terminationGracePeriod = 60L;
    private String dockerImagePath;
    private String registryImagePath;
    private String subdomain;
    private boolean shouldHost = true;
    private String command;
    private List<Volume> volumes = new ArrayList<>();
    private Map<String, String> environmentVariables;
    private List<AutoCD> otherImages = new ArrayList<>();
    private List<String> args = new ArrayList<>();
    private String serviceName = null;

    public AutoCD(int containerPort, int servicePort, boolean publiclyAccessible, long terminationGracePeriod, String dockerImagePath, String registryImagePath, String subdomain, boolean shouldHost, String command, List<Volume> volumes, Map<String, String> environmentVariables, List<AutoCD> otherImages, List<String> args, String serviceName) {
        this.containerPort = containerPort;
        this.servicePort = servicePort;
        this.publiclyAccessible = publiclyAccessible;
        this.terminationGracePeriod = terminationGracePeriod;
        this.dockerImagePath = dockerImagePath;
        this.registryImagePath = registryImagePath;
        this.subdomain = subdomain;
        this.shouldHost = shouldHost;
        this.command = command;
        this.volumes = volumes;
        this.environmentVariables = environmentVariables;
        this.otherImages = otherImages;
        this.args = args;
        this.serviceName = serviceName;
    }

    public AutoCD() {
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
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

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
