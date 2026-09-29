package dev.langchain4j.community.rag.benchmark.targets.arcadedb;

import com.arcadedb.Constants;

/**
 * The ArcadeDB version the benchmark runs against.
 *
 * <p>It is read from the ArcadeDB engine jar on the classpath, so the single source of truth is the
 * {@code arcadedb.version} Maven property the store module was built with ({@code -Darcadedb.version=X}).
 * Remote mode uses the Docker image of the same version.
 */
public final class ArcadeDbVersion {

    public static final String DOCKER_IMAGE_REPOSITORY = "arcadedata/arcadedb";

    private ArcadeDbVersion() {}

    /**
     * Returns the version of the ArcadeDB engine on the classpath, e.g. {@code 26.7.2}.
     */
    public static String onClasspath() {
        return Constants.getRawVersion();
    }

    /**
     * Returns the Docker image matching {@link #onClasspath()}, e.g. {@code arcadedata/arcadedb:26.7.2}.
     */
    public static String dockerImage() {
        return DOCKER_IMAGE_REPOSITORY + ":" + onClasspath();
    }
}
