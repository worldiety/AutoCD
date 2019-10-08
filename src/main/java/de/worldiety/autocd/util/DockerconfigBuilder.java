package de.worldiety.autocd.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Base64;
import java.util.Map;

public class DockerconfigBuilder {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static String getDockerConfig(String registry, String username, String token) {
        var auth = String.format("%s:%s", username, token);
        var authEncoded = Base64.getEncoder().encodeToString(auth.getBytes());
        System.out.println(authEncoded);
        var authItems = Map.of(registry, new Dockerconfig.AuthItem(authEncoded));

        var conf = new Dockerconfig(authItems);
        return GSON.toJson(conf);
    }
}
