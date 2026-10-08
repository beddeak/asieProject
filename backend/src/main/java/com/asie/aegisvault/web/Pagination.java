package com.asie.aegisvault.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component("pagination")
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(
    type =
        org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
public class Pagination {
  private final HttpServletRequest request;

  public String link(String parameter, int page) {
    return UriComponentsBuilder.fromPath(request.getRequestURI())
        .query(request.getQueryString())
        .replaceQueryParam(parameter, page)
        .build(true)
        .toUriString();
  }
}
