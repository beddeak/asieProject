package com.asie.aegisvault.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

class NavigationAdviceTest {
  private final NavigationAdvice navigation = new NavigationAdvice();

  @ParameterizedTest
  @CsvSource({
    "/,home",
    "/document/versions/17,documents",
    "/document/review,documents",
    "/projects/42/work,projects",
    "/releases/9,projects",
    "/departments/3/notices/4,departments",
    "/admin/users,admin",
    "/dep/create,admin",
    "/account,account",
    "/notifications,notifications",
    "/user/login,login",
    "/user/signup,signup",
    "/projects-archive,''"
  })
  void resolvesNestedRoutesInsideAnApplicationContext(String path, String expected) {
    var request = new MockHttpServletRequest("GET", "/aegis" + path);
    request.setContextPath("/aegis");

    assertEquals(expected, navigation.currentPage(request));
  }
}
