package com.asie.aegisvault.project;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Read-only guidance derived from the same conditions enforced at release time. */
public record ProjectProgress(List<Step> steps, Step nextStep, String message, boolean restricted) {
  public ProjectProgress {
    steps = List.copyOf(steps);
  }

  public enum Key {
    DOCUMENTS,
    QUALITY,
    SECURITY,
    RELEASE
  }

  @Getter
  @RequiredArgsConstructor
  public enum State {
    COMPLETE("완료"),
    NEEDS_ACTION("진행 필요"),
    BLOCKED("차단");
    private final String label;
  }

  public record Action(String label, String href) {}

  public record Step(
      Key key, String title, State state, String summary, String owner, List<Action> actions) {
    public Step {
      actions = List.copyOf(actions);
    }
  }
}
