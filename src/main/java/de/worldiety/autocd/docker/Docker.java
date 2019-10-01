package de.worldiety.autocd.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.PushResponseItem;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.BuildImageResultCallback;
import com.github.dockerjava.core.command.PushImageResultCallback;
import de.worldiety.autocd.util.Environment;
import java.io.File;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Docker {
    private static final Logger log = LoggerFactory.getLogger(Docker.class);
    private DockerClient client;

    public Docker() {
        var reg = System.getenv(Environment.CI_REGISTRY.toString());
        DefaultDockerClientConfig config;
        if (reg != null) {
            config = DefaultDockerClientConfig
                    .createDefaultConfigBuilder()
                    .withRegistryUrl(reg)
                    .withRegistryEmail(System.getenv(Environment.CI_REGISTRY_EMAIL.toString()))
                    .withRegistryUsername(System.getenv(Environment.CI_REGISTRY_USER.toString()))
                    .withRegistryPassword(System.getenv(Environment.CI_REGISTRY_PASSWORD.toString()))
                    .build();

        } else {
            config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        }
        client = DockerClientBuilder.getInstance(config).build();
    }

    public String buildAndPushImageFromFile(File configFile) {
        var reg = System.getenv(Environment.CI_REGISTRY.toString());
        var projectName = System.getenv(Environment.CI_PROJECT_NAME.toString());
        var nameSpace = System.getenv(Environment.CI_PROJECT_NAMESPACE.toString());

        reg = reg == null ? "default" : reg;
        projectName = projectName == null ? "default" : projectName;
        nameSpace = nameSpace == null ? "default" : nameSpace;

        var tag = reg + "/" + nameSpace + "/" + projectName;

        log.info("creating image with tag " + tag);

        BuildImageResultCallback callback = new BuildImageResultCallback() {
            @Override
            public void onNext(@NotNull BuildResponseItem item) {
                if (item.getStream() != null && !item.getStream().equals(".")) {
                    log.info(item.getStream());
                }
                super.onNext(item);
            }
        };

        var staticDir = new File("static/");
        if (!staticDir.exists()) {
            if (!staticDir.mkdir()) {
                log.error("No write permissions");
            }
        }

        client.buildImageCmd(configFile)
                .withTags(Set.of(tag))
                .exec(callback)
                .awaitImageId();

        try {
            client.pushImageCmd(tag).exec(new PushImageResultCallback() {
                @Override
                public void onNext(PushResponseItem item) {
                    if (item.getStream() != null && !item.getStream().equals(".")) {
                        log.info(item.getStream());
                    }
                    super.onNext(item);
                }
            }).awaitCompletion();
        } catch (InterruptedException e) {
            log.error("pushing image failed", e);
        }

        return tag;
    }
}
