package de.worldiety.autocd.util;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class Dockerconfig {
    @SerializedName("auths")
    private final Map<String, AuthItem>  authItems;

    public Dockerconfig(Map<String, AuthItem> authItems) {
        this.authItems = authItems;
    }

    public Map<String, AuthItem> getAuthItems() {
        return authItems;
    }

    public static class AuthItem {
        private final String auth;

        public AuthItem(String auth) {
            this.auth = auth;
        }

        public String getAuth() {
            return auth;
        }
    }
}
