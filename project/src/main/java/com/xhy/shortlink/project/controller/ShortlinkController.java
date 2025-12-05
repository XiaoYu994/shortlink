package com.xhy.shortlink.project.controller;

import com.xhy.shortlink.project.service.ShortlinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ShortlinkController {
    private final ShortlinkService shortlinkService;
}
