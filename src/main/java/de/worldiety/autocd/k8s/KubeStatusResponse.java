package de.worldiety.autocd.k8s;

import com.google.gson.annotations.SerializedName;

public class KubeStatusResponse {
    @SerializedName("kind")
    private final String kind;

    @SerializedName("apiVersion")
    private final String apiVersion;

    @SerializedName("metadata")
    private final Metadata metadata;

    @SerializedName("status")
    private final String status;

    @SerializedName("message")
    private final String message;

    @SerializedName("reason")
    private final String reason;

    @SerializedName("details")
    private final Details details;

    @SerializedName("code")
    private final int code;

    public KubeStatusResponse(String kind, String apiVersion, Metadata metadata, String status,
                              String message, String reason, Details details, int code) {
        this.kind = kind;
        this.apiVersion = apiVersion;
        this.metadata = metadata;
        this.status = status;
        this.message = message;
        this.reason = reason;
        this.details = details;
        this.code = code;
    }

    public String getKind() {
        return kind;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    @Override
    public String toString() {
        return "KubeStatusResponse{" +
                "kind='" + kind + '\'' +
                ", apiVersion='" + apiVersion + '\'' +
                ", metadata=" + metadata +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", reason='" + reason + '\'' +
                ", details=" + details +
                ", code=" + code +
                '}';
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getReason() {
        return reason;
    }

    public Details getDetails() {
        return details;
    }

    public int getCode() {
        return code;
    }

    public static class Metadata {
        public Metadata() {
        }
    }

    public static class Details {
        @SerializedName("name")
        private final String name;

        @SerializedName("kind")
        private final String kind;

        public Details(String name, String kind) {
            this.name = name;
            this.kind = kind;
        }

        public String getName() {
            return name;
        }

        public String getKind() {
            return kind;
        }
    }
}
