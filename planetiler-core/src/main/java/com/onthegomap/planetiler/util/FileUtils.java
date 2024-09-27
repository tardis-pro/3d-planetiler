package com.onthegomap.planetiler.util;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convenience methods for working with files on disk.
 */
public class FileUtils {

  private static final Format FORMAT = Format.defaultInstance();
  // Prevent zip-bomb attack, see https://rules.sonarsource.com/java/RSPEC-5042
  private static final int ZIP_THRESHOLD_ENTRIES = 10_000;
  private static final long ZIP_THRESHOLD_SIZE = 100_000_000_000L;
  private static final double ZIP_THRESHOLD_RATIO = 1_000;

  private static final Logger LOGGER = LoggerFactory.getLogger(FileUtils.class);

  private FileUtils() {}

  /** Returns a stream that lists all files in {@code fileSystem}. */
  public static Stream<Path> walkFileSystem(FileSystem fileSystem) {
    return StreamSupport.stream(fileSystem.getRootDirectories().spliterator(), false)
      .flatMap(rootDirectory -> {
        try {
          return Files.walk(rootDirectory, FileVisitOption.FOLLOW_LINKS);
        } catch (IOException e) {
          LOGGER.error("Unable to walk " + rootDirectory + " in " + fileSystem, e);
          return Stream.empty();
        }
      });
  }

  /**
   * Returns list of paths matching {@param pattern} within {@param basePath}.
   * <p>
   * If {@param basePath} is a directory, then {@param walkZipFile} will be invoked for each matching {@code .zip} file
   * found. This function should return paths of interest within the zip file.
   *
   * @param basePath    file path to recursively walk, either a directory or ZIP archive.
   * @param pattern     pattern to match filenames against, as described in {@link FileSystem#getPathMatcher(String)}.
   * @param walkZipFile callback function to recurse into matching {@code .zip} files.
   */
  public static List<Path> walkPathWithPattern(Path basePath, String pattern,
    Function<Path, List<Path>> walkZipFile) {
    PathMatcher matcher = basePath.getFileSystem().getPathMatcher("glob:" + pattern);

    try {
      if (FileUtils.hasExtension(basePath, "zip")) {
        try (
          var zipFs = FileSystems.newFileSystem(basePath);
          var walkStream = FileUtils.walkFileSystem(zipFs)
        ) {
          return walkStream
            .filter(p -> p.getFileName() != null && matcher.matches(p.getFileName()))
            .toList();
        }
      } else if (Files.isDirectory(basePath)) {
        try (var walk = Files.walk(basePath, FileVisitOption.FOLLOW_LINKS)) {
          return walk
            .filter(path -> matcher.matches(path.getFileName()) || matcher.matches(basePath.relativize(path)))
            .flatMap(path -> {
              if (FileUtils.hasExtension(path, "zip")) {
                return walkZipFile.apply(path).stream();
              } else {
                return Stream.of(path);
              }
            })
            .toList();
        }
      } else {
        throw new IllegalArgumentException("No files matching " + basePath + "/" + pattern);
      }
    } catch (IOException exc) {
      throw new UncheckedIOException(exc);
    }
  }

  /**
   * Returns list of paths matching {@param pattern} within {@param basePath}.
   *
   * @param basePath file path to recursively walk, either a directory or ZIP archive.
   * @param pattern  pattern to match filenames against, as described in {@link FileSystem#getPathMatcher(String)}.
   */
  public static List<Path> walkPathWithPattern(Path basePath, String pattern) {
    return walkPathWithPattern(basePath, pattern, List::of);
  }


  /** Returns true if {@code path} ends with ".extension" (case-insensitive). */
  public static boolean hasExtension(Path path, String extension) {
    return path.toString().toLowerCase().endsWith("." + extension.toLowerCase());
  }

  /** Returns the size of {@code path} as a file, or 0 if missing/inaccessible. */
  public static long fileSize(Path path) {
    try {
      return Files.size(path);
    } catch (IOException e) {
      return 0;
    }
  }

  /** Returns the directory usage of all files until {@code path} or 0 if missing/inaccessible. */
  public static long directorySize(Path path) {
    try (var walker = Files.walk(path)) {
      return walker
        .filter(Files::isRegularFile)
        .mapToLong(FileUtils::fileSize)
        .sum();
    } catch (IOException e) {
      return 0;
    }
  }

  /** Returns the size of a directory or file at {@code path} or 0 if missing/inaccessible. */
  public static long size(Path path) {
    return Files.isDirectory(path) ? directorySize(path) : fileSize(path);
  }

  /** Deletes a file if it exists or fails silently if it doesn't. */
  public static void deleteFile(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      LOGGER.error("Unable to delete " + path, e);
    }
  }

  /** Deletes all files under a directory and fails silently if it doesn't exist. */
  public static void deleteDirectory(Path path) {
    try (var walker = Files.walk(path)) {
      walker
        .sorted(Comparator.reverseOrder())
        .forEach(FileUtils::deleteFile);
    } catch (NoSuchFileException e) {
      // this is OK, file doesn't exist, so can't walk
    } catch (IOException e) {
      LOGGER.error("Unable to delete " + path, e);
    }
  }

  /** Deletes files or directories recursively, failing silently if missing. */
  public static void delete(Path... paths) {
    for (Path path : paths) {
      if (Files.isDirectory(path)) {
        deleteDirectory(path);
      } else {
        deleteFile(path);
      }
    }
  }

  /** Returns the {@link FileStore} for {@code path}, or its nearest parent directory if it does not exist yet. */
  public static FileStore getFileStore(final Path path) {
    Path absolutePath = path.toAbsolutePath();
    IOException exception = null;
    while (absolutePath != null) {
      try {
        return Files.getFileStore(absolutePath);
      } catch (IOException e) {
        exception = e;
        absolutePath = absolutePath.getParent();
      }
    }
    throw new UncheckedIOException("Cannot get file store for " + path, exception);
  }

  /**
   * Moves a file.
   *
   * @throws UncheckedIOException if an error occurs
   */
  public static void move(Path from, Path to) {
    try {
      Files.move(from, to);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Ensures a directory and all parent directories exists.
   *
   * @throws IllegalStateException if an error occurs
   */
  public static void createDirectory(Path path) {
    try {
      Files.createDirectories(path);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create directories " + path, e);
    }
  }

  /**
   * Ensures all parent directories of each path in {@code paths} exist.
   *
   * @throws IllegalStateException if an error occurs
   */
  public static void createParentDirectories(Path... paths) {
    for (var path : paths) {
      if (path != null) {
        try {
          if (Files.isDirectory(path) && !Files.exists(path)) {
            Files.createDirectories(path);
          } else {
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
              Files.createDirectories(parent);
            }
          }
        } catch (IOException e) {
          throw new IllegalStateException("Unable to create parent directories " + path, e);
        }
      }
    }
  }

  /**
   * Attempts to delete the file located at {@code path} on normal JVM exit.
   */
  public static void deleteOnExit(Path path) {
    path.toFile().deleteOnExit();
  }

  /**
   * Unzips a zip file on the classpath to {@code destDir}.
   *
   * @throws UncheckedIOException if an IO exception occurs
   */
  public static void unzipResource(String resource, Path dest) {
    try (var is = FileUtils.class.getResourceAsStream(resource)) {
      Objects.requireNonNull(is, "Resource not found on classpath: " + resource);
      unzip(is, dest);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Copies bytes from {@code input} to {@code destPath}, ensuring that the size is limited to a reasonable value.
   *
   * @throws UncheckedIOException if an IO exception occurs
   */
  public static void safeCopy(InputStream inputStream, Path destPath) {
    try (var outputStream = Files.newOutputStream(destPath, StandardOpenOption.CREATE, WRITE)) {
      long totalSize = 0;

      int nBytes;
      byte[] buffer = new byte[2048];
      while ((nBytes = inputStream.read(buffer)) > 0) {
        outputStream.write(buffer, 0, nBytes);
        totalSize += nBytes;

        if (totalSize > ZIP_THRESHOLD_SIZE) {
          throw new IOException("The uncompressed data size " + FORMAT.storage(totalSize) +
            "B is too much for the application resource capacity");
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Unzips a zip file from an input stream to {@code destDir}.
   *
   * @throws UncheckedIOException if an IO exception occurs
   */
  public static void unzip(InputStream input, Path destDir) {
    long totalSizeArchive = 0;
    int totalEntryArchive = 0;
    try (var zip = new ZipInputStream(input)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        Path targetDirResolved = destDir.resolve(entry.getName());
        Path destination = targetDirResolved.normalize();
        if (!destination.startsWith(destDir)) {
          throw new IOException("Bad zip entry: " + entry.getName());
        }
        if (entry.isDirectory()) {
          FileUtils.createDirectory(destDir);
        } else {
          createParentDirectories(destination);

          // Instead of Files.copy, read 2kB at a time to prevent zip bomb attack, see https://rules.sonarsource.com/java/RSPEC-5042
          int nBytes;
          byte[] buffer = new byte[2048];
          int totalSizeEntry = 0;

          try (
            var out = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW,
              WRITE)
          ) {
            totalEntryArchive++;
            while ((nBytes = zip.read(buffer)) > 0) {
              out.write(buffer, 0, nBytes);
              totalSizeEntry += nBytes;
              totalSizeArchive += nBytes;

              double compressionRatio = totalSizeEntry * 1d / entry.getCompressedSize();
              if (compressionRatio > ZIP_THRESHOLD_RATIO) {
                throw new IOException(
                  "Ratio between compressed and uncompressed data is highly suspicious " +
                    FORMAT.numeric(compressionRatio) +
                    "x, looks like a Zip Bomb Attack");
              }
            }

            if (totalSizeArchive > ZIP_THRESHOLD_SIZE) {
              throw new IOException("The uncompressed data size " + FORMAT.storage(totalSizeArchive) +
                "B is too much for the application resource capacity");
            }

            if (totalEntryArchive > ZIP_THRESHOLD_ENTRIES) {
              throw new IOException("Too many entries in this archive " + FORMAT.integer(totalEntryArchive) +
                ", can lead to inodes exhaustion of the system");
            }
          }
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static long getLastModifiedTime(Path path) throws IOException {
    try {
      FileTime time;
      if ("jar".equals(path.toUri().getScheme())) {
        time = Files.getLastModifiedTime(Path.of(path.getFileSystem().toString()));
      } else {
        time = Files.getLastModifiedTime(path);
      }
      return time.toMillis();
    } catch (ClosedFileSystemException e) {
      throw new IOException("File system closed", e);
    }
  }

  /** Returns {@code true} if src is newer than dest, or if dest does not exist. Defaults to true if an error occurs. */
  public static boolean isNewer(Path src, Path dest) {
    try {
      return Files.notExists(dest) || getLastModifiedTime(src) > getLastModifiedTime(dest);
    } catch (IOException e) {
      return true;
    }
  }

  /** Expands the file at {@code path} to {@code size} bytes. */
  public static void setLength(Path path, long size) {
    try (var fc = FileChannel.open(path, CREATE, WRITE)) {
      int written = fc.write(ByteBuffer.allocate(1), size - 1);
      if (written != 1) {
        throw new IOException("Unable to expand " + path + " to " + size);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
