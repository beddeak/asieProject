package com.asie.aegisvault.attachment;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FileStore {
  private final Path root;
  private final long maxBytes;

  public FileStore(
      @Value("${app.files.path:./local/files}") String root,
      @Value("${app.files.max-bytes:10485760}") long maxBytes) {
    this.root = Path.of(root).toAbsolutePath().normalize();
    this.maxBytes = maxBytes;
    try {
      Files.createDirectories(this.root);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public Stored put(MultipartFile file) {
    if (file.isEmpty() || file.getSize() > maxBytes)
      throw new IllegalArgumentException("비어 있거나 업로드 용량을 초과한 파일입니다.");
    String name =
        Optional.ofNullable(file.getOriginalFilename()).orElse("attachment").replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_");
    if (name.isBlank() || name.length() > 255)
      throw new IllegalArgumentException("파일 이름은 1~255자로 입력해주세요.");
    String ext =
        name.contains(".")
            ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
            : "";
    String type =
        switch (ext) {
          case "pdf" -> "application/pdf";
          case "png" -> "image/png";
          case "jpg", "jpeg" -> "image/jpeg";
          case "txt" -> "text/plain";
          case "csv" -> "text/csv";
          default -> throw new IllegalArgumentException("PDF, PNG, JPG, TXT, CSV 파일을 지원합니다.");
        };
    String key = UUID.randomUUID().toString();
    Path path = path(key);
    try (InputStream input = file.getInputStream();
        OutputStream output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[8192];
      int read;
      long size = 0;
      boolean first = true;
      while ((read = input.read(buffer)) != -1) {
        size += read;
        if (size > maxBytes) throw new IllegalArgumentException("업로드 용량을 초과했습니다.");
        if (first) {
          validateHeader(ext, buffer, read);
          first = false;
        }
        digest.update(buffer, 0, read);
        output.write(buffer, 0, read);
      }
      if (size == 0) throw new IllegalArgumentException("빈 파일입니다.");
      return new Stored(key, name, type, size, HexFormat.of().formatHex(digest.digest()));
    } catch (IOException | NoSuchAlgorithmException | RuntimeException e) {
      delete(key);
      if (e instanceof RuntimeException runtime) throw runtime;
      throw new IllegalStateException("파일 저장에 실패했습니다.", e);
    }
  }

  private void validateHeader(String ext, byte[] b, int n) {
    boolean valid =
        switch (ext) {
          case "pdf" ->
              n >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-';
          case "png" ->
              n >= 8
                  && b[0] == (byte) 137
                  && b[1] == 'P'
                  && b[2] == 'N'
                  && b[3] == 'G'
                  && b[4] == 13
                  && b[5] == 10
                  && b[6] == 26
                  && b[7] == 10;
          case "jpg", "jpeg" ->
              n >= 3 && b[0] == (byte) 255 && b[1] == (byte) 216 && b[2] == (byte) 255;
          default -> true;
        };
    if (!valid) throw new IllegalArgumentException("파일 확장자와 실제 형식이 일치하지 않습니다.");
  }

  public Path path(String key) {
    if (!key.matches("[0-9a-f-]{36}")) throw new IllegalArgumentException("잘못된 저장 키입니다.");
    return root.resolve(key);
  }

  public void delete(String key) {
    try {
      Files.deleteIfExists(path(key));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public boolean intact(Attachment file) {
    try (InputStream input = Files.newInputStream(path(file.getStorageKey()))) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] buffer = new byte[8192];
      int n;
      long size = 0;
      while ((n = input.read(buffer)) != -1) {
        digest.update(buffer, 0, n);
        size += n;
      }
      return size == file.getSize()
          && MessageDigest.isEqual(digest.digest(), HexFormat.of().parseHex(file.getSha256()));
    } catch (IOException e) {
      return false;
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public record Stored(String key, String name, String type, long size, String hash) {}
}
