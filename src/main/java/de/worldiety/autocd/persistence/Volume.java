package de.worldiety.autocd.persistence;

import java.util.Objects;
import org.jetbrains.annotations.Contract;

public class Volume {
    private String volumeMount;
    private String volumeSize = "1Gi";
    private String filePermission;

    public Volume(String volumeMount, String volumeSize, String filePermission) {
        this.volumeMount = volumeMount;
        this.volumeSize = volumeSize;
        this.filePermission = filePermission;
    }

    @Contract(pure = true)
    public Volume() {
    }

    public String getFilePermission() {
        return filePermission;
    }

    public void setFilePermission(String filePermission) {
        this.filePermission = filePermission;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Volume volume = (Volume) o;
        return Objects.equals(volumeMount, volume.volumeMount) &&
                Objects.equals(volumeSize, volume.volumeSize);
    }

    @Override
    public int hashCode() {
        return Objects.hash(volumeMount, volumeSize);
    }

    public String getVolumeMount() {
        return volumeMount;
    }

    public void setVolumeMount(String volumeMount) {
        this.volumeMount = volumeMount;
    }

    public String getVolumeSize() {
        return volumeSize;
    }

    public void setVolumeSize(String volumeSize) {
        this.volumeSize = volumeSize;
    }
}
