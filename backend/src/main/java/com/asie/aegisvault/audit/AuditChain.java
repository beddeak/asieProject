package com.asie.aegisvault.audit;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuditChain {
  private final AuditHeadRepository heads;
  private final AuditRecordRepository records;
  private final TransactionTemplate transactions;
  private final Path keyFile;
  private byte[] key;

  public AuditChain(
      AuditHeadRepository heads,
      AuditRecordRepository records,
      PlatformTransactionManager manager,
      @Value("${app.audit.key-file:./local/audit.key}") String keyFile) {
    this.heads = heads;
    this.records = records;
    this.transactions = new TransactionTemplate(manager);
    this.keyFile = Path.of(keyFile).toAbsolutePath().normalize();
  }

  @PostConstruct
  void initialize() throws IOException {
    Files.createDirectories(keyFile.getParent());
    synchronized (AuditChain.class) {
      try (var channel =
              java.nio.channels.FileChannel.open(
                  keyFile.resolveSibling(keyFile.getFileName() + ".lock"),
                  StandardOpenOption.CREATE,
                  StandardOpenOption.WRITE);
          var ignored = channel.lock()) {
        if (!Files.exists(keyFile)) {
          if (records.count() > 0)
            throw new IllegalStateException("기존 감사 기록의 서명 키가 없습니다. 백업한 키를 복원해주세요.");
          byte[] generated = new byte[32];
          new SecureRandom().nextBytes(generated);
          if (Files.getFileStore(keyFile.getParent()).supportsFileAttributeView("posix"))
            Files.createFile(
                keyFile,
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
          else Files.createFile(keyFile);
          Files.writeString(keyFile, Base64.getEncoder().encodeToString(generated));
        }
        key = Base64.getDecoder().decode(Files.readString(keyFile).strip());
        if (key.length != 32) throw new IllegalStateException("감사 로그 서명 키는 32바이트여야 합니다.");
      }
    }
    try {
      transactions.executeWithoutResult(
          tx -> {
            if (!heads.existsById(1L)) heads.saveAndFlush(AuditHead.initial());
          });
    } catch (DataIntegrityViolationException concurrentInitialization) {
      if (!Boolean.TRUE.equals(transactions.execute(tx -> heads.existsById(1L))))
        throw concurrentInitialization;
    }
  }

  @Transactional
  public AuditRecord append(AuditEvent event) {
    AuditHead head = heads.lockHead().orElseThrow(() -> new IllegalStateException("감사 기준점이 없습니다."));
    AuditRecord record =
        new AuditRecord(
            head.getSequence() + 1,
            event,
            Instant.now().truncatedTo(ChronoUnit.MILLIS),
            head.getRecordHash());
    record.sign(hash(record));
    records.save(record);
    head.advance(record);
    return record;
  }

  @Transactional
  public Verification verify() {
    AuditHead head = heads.lockHead().orElseThrow();
    if (records.count() != head.getSequence())
      return new Verification(false, head.getSequence(), "기준점과 감사 기록 수가 일치하지 않습니다.");
    long through = head.getSequence();
    String finalHash = head.getRecordHash();
    long cursor = 0;
    String previous = "0".repeat(64);
    while (cursor < through) {
      var batch =
          records.findByIdGreaterThanAndIdLessThanEqualOrderById(
              cursor, through, PageRequest.of(0, 500));
      if (batch.isEmpty()) return new Verification(false, cursor, "감사 기록이 누락되었습니다.");
      for (AuditRecord record : batch) {
        if (record.getId() != cursor + 1
            || !previous.equals(record.getPreviousHash())
            || !MessageDigest.isEqual(
                hash(record).getBytes(StandardCharsets.US_ASCII),
                record.getRecordHash().getBytes(StandardCharsets.US_ASCII)))
          return new Verification(false, record.getId(), "감사 기록의 연결 또는 서명이 일치하지 않습니다.");
        cursor = record.getId();
        previous = record.getRecordHash();
      }
    }
    return new Verification(
        previous.equals(finalHash),
        cursor,
        previous.equals(finalHash) ? "현재 기준점까지 모든 기록의 연결과 서명이 일치합니다." : "마지막 기록과 기준점이 일치하지 않습니다.");
  }

  private String hash(AuditRecord record) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (DataOutputStream out = new DataOutputStream(bytes)) {
        for (Object value :
            new Object[] {
              record.getId(),
              record.getActor(),
              record.getAction(),
              record.getTargetType(),
              record.getTargetId(),
              record.getProjectId(),
              record.getDescription(),
              record.getOccurredAt(),
              record.getPreviousHash()
            }) {
          if (value == null) out.writeInt(-1);
          else {
            byte[] field = value.toString().getBytes(StandardCharsets.UTF_8);
            out.writeInt(field.length);
            out.write(field);
          }
        }
      }
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(bytes.toByteArray()));
    } catch (GeneralSecurityException | IOException exception) {
      throw new IllegalStateException("감사 서명을 생성하지 못했습니다.", exception);
    }
  }

  public record Verification(boolean valid, long sequence, String message) {}
}
