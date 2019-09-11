package de.worldiety.autocd.persistence;

import de.worldiety.autocd.util.FileType;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class FileFinder {
    private List<File> fileList = new ArrayList<>();
    private FileType type;

    public FileFinder(String path) {
        prepFileList(path);
    }

    @NotNull
    @Contract("_ -> new")
    private InputStream getFileFromResources(String fileName) {
        var classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = getClass().getClassLoader();
        }
        return Objects.requireNonNull(classLoader.getResourceAsStream(fileName));
    }

    public List<File> getFileList() {
        return fileList;
    }

    public void setFileList(List<File> fileList) {
        this.fileList = fileList;
    }

    private void prepFileList(String directoryPath) {
        var directory = new File(directoryPath);

        var fList = directory.listFiles();

        if (fList == null) {
            throw new IllegalArgumentException(directoryPath + " is not a directory!");
        }

        for (var file : fList) {
            if (file.isFile()) {
                fileList.add(file);
            } else if (file.isDirectory()) {
                prepFileList(file.getAbsolutePath());
            }
        }
    }

    private String getFileExt(@NotNull File listFile) {
        return FilenameUtils.getExtension(listFile.getAbsolutePath());
    }

    @Contract(pure = true)
    private FileType mapFileTypeToFileType(@NotNull String ext) {
        switch (ext) {
            case "go":
                return FileType.GO;
            case "java":
                return FileType.JAVA;
            case "vue":
                return FileType.VUE;
            default:
                return FileType.OTHER;
        }
    }

    public FileType getType() {
        return type;
    }

    public void setType(FileType type) {
        this.type = type;
    }

    public Optional<File> findDockerConfig() {
        var opt = fileList.stream()
                .map(this::getFileExt)
                .map(this::mapFileTypeToFileType)
                .filter(it -> it.getDockerConfig() != null)
                .findFirst();

        return opt.map(ftype -> {
            this.type = ftype;
            var nFile = new File("Dockerfile");
            System.out.println(nFile);
            try {
                var fout = new FileOutputStream(nFile);

                IOUtils.copy(getFileFromResources(ftype.getDockerConfig()), fout);
                fout.flush();

                if (ftype.equals(FileType.JAVA)) {
                    IOUtils.copy(getFileFromResources(ftype.getFinalDocker()), fout);
                    fout.flush();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }

            return nFile.getAbsoluteFile();
        });
    }

}
