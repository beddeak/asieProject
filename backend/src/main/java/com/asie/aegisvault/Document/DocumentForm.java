package com.asie.aegisvault.Document;

import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.security.SecurityClassification;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
public class DocumentForm {
  @NotBlank
  @Size(max = 50)
  private String title;

  private String content;
  @NotNull private Position requiredPosition = Position.STAFF;
  private Long projectId;
  @NotNull private DocumentCategory category = DocumentCategory.OTHER;
  @NotNull private SecurityClassification classification = SecurityClassification.INTERNAL;
  private Long revision;
}
