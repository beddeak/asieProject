package com.asie.aegisvault.release;

/** The manifest and ZIP writer share exactly the same entry names. */
public final class ReleasePackagePaths {
  private ReleasePackagePaths() {}

  public static String document(Long versionId) {
    return "documents/version-" + versionId + ".html";
  }

  public static String attachment(Long versionId, Long attachmentId, String filename) {
    return "attachments/" + versionId + "/" + attachmentId + "-" + filename;
  }
}
