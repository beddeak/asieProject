package com.asie.aegisvault.Document;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Sort;

@Getter
@Setter
public class DocumentFilter {
  private String keyword;
  private String author;
  private DocumentStatus status;
  private DocumentCategory category;
  private Long projectId;
  private Long departmentId;
  private boolean archived;
  private boolean mine;
  private boolean reviewOnly;
  private boolean securityOnly;
  private String sort = "newest";

  public Sort ordering() {
    return switch (sort == null ? "newest" : sort) {
      case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt", "id");
      case "oldest" -> Sort.by("createdAt", "id");
      case "title" -> Sort.by("title", "id");
      default -> throw new IllegalArgumentException("올바른 정렬 기준을 선택해주세요.");
    };
  }
}
