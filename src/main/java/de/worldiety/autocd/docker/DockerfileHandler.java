package de.worldiety.autocd.docker;

import de.worldiety.autocd.persistence.AutoCD;
import de.worldiety.autocd.util.FileType;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DockerfileHandler {
    private static final Logger log = LoggerFactory.getLogger(DockerfileHandler.class);
    private List<File> fileList = new ArrayList<>();

    public DockerfileHandler(String path) {
        prepFileList(path);
    }

    /**
     * returns a specified file from the resource folder
     *
     * @param fileName
     * @return
     */
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

    /**
     * Will list all files within a given directory. If there is another directory found, it will step into that
     * directory and will list those files as well.
     *
     * @param directoryPath
     */
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

    /**
     * returns the file type based on its extension.
     *
     * @param listFile
     * @return String
     */
    private String getFileExt(@NotNull File listFile) {
        return FilenameUtils.getExtension(listFile.getAbsolutePath());
    }

    /**
     * Mps the found file extension to the fitting file type
     *
     * @param ext
     * @return FileType
     */
    @Contract(pure = true)
    private FileType mapFileTypeToFileType(@NotNull String ext) {
        //noinspection IfCanBeSwitch
        if ("go".equals(ext)) {
            return FileType.GO;
        } else if (".rs".equals(ext)) {
            var rocketConfig = new File("Rocket.toml");
            if (rocketConfig.isFile()) {
                return FileType.RUST_ROCKET;
            }
            return FileType.RUST;
        } else if ("java".equals(ext)) {
            return FileType.JAVA;
        } else if ("vue".equals(ext)) {
            var isNuxt = new File("nuxt.config.js").exists();
            return isNuxt ? FileType.NUXT : FileType.VUE;
        } else if ("swift".equals(ext)) {
            return FileType.SWIFT;
        } else if ("ts".equals(ext) || "js".equals(ext)) {
            var packageJson = new File("package.json");

            try {
                var packStr = Files.readString(packageJson.toPath());
                if (packStr.contains("@kloudsoftware/eisen")) {
                    return FileType.EISEN;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            return FileType.OTHER;
        }
        return FileType.OTHER;
    }


    public FileType getFileType() {
        return fileList.stream()
                .map(this::getFileExt)
                .map(this::mapFileTypeToFileType)
                .filter(it -> it.getDockerConfig() != null)
                .findFirst().orElse(FileType.OTHER);
    }

    /**
     * This method will create a fitting docker configuration based on the FileType.
     * If the project already has a build.sh file, autoCD will use the given one. If there is none, it will create a default
     * build.sh file.
     *
     * @return File
     */
    public Optional<File> findDockerConfig(AutoCD autoCD, String buildType) {
        var opt = fileList.stream()
                .map(this::getFileExt)
                .map(this::mapFileTypeToFileType)
                .filter(it -> it.getDockerConfig() != null)
                .findFirst();

        return opt.map(ftype -> {
            var customBuildsh = new File("build.sh");
            var nFile = new File("Dockerfile");

            try {
                var fout = new FileOutputStream(nFile);

                IOUtils.copy(getFileFromResources(ftype.getDockerConfig()), fout);
                fout.flush();

                var bwout = new BufferedWriter(new OutputStreamWriter(fout));

                if (autoCD.getBuildVariables().containsKey(buildType)) {
                    autoCD.getBuildVariables().get(buildType).forEach((k, v) -> {
                        if (v.startsWith("${")) {
                            v = v.substring(2, v.length() - 1);
                            var initial = v;
                            v = System.getenv(v);

                            if (v == null) {
                                log.warn("Unable to resolve environment variable: " + initial);
                            }
                        }

                        try {
                            System.out.println("setting: " + "ENV " + k + " " + v + "\n");
                            bwout.write("ENV " + k + " " + v + "\n");
                            bwout.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }

                if (customBuildsh.exists()) {
                    IOUtils.copy(getFileFromResources("run-build-part"), fout);
                } else {
                    var bfout = new BufferedWriter(new OutputStreamWriter(fout));
                    bfout.write(ftype.getDefaultBuild());
                    bfout.flush();
                }

                IOUtils.copy(getFileFromResources(ftype.getFinalDocker()), fout);
                fout.flush();

            } catch (IOException e) {
                e.printStackTrace();
            }

            return nFile.getAbsoluteFile();
        });
    }
}
