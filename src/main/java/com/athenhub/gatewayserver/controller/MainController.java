package com.athenhub.gatewayserver.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.reactive.result.view.RedirectView;

@Controller
public class MainController {

  @GetMapping("/")
  public RedirectView index() {
    return new RedirectView("/api-docs.html");
  }
}
