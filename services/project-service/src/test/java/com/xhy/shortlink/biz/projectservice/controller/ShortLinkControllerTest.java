/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xhy.shortlink.biz.projectservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkUpdateReqDTO;
import com.xhy.shortlink.biz.projectservice.service.ShortLinkCoreService;
import com.xhy.shortlink.biz.projectservice.service.UrlTitleService;
import com.xhy.shortlink.biz.projectservice.service.impl.ShortLinkRedirectServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ShortLinkControllerTest {

    @InjectMocks
    private ShortLinkController shortLinkController;

    @Mock
    private ShortLinkCoreService shortLinkCoreService;

    @Mock
    private ShortLinkRedirectServiceImpl shortLinkRedirectService;

    @Mock
    private UrlTitleService urlTitleService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void updateShortLink_usesPut() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(shortLinkController).build();
        ShortLinkUpdateReqDTO requestParam = new ShortLinkUpdateReqDTO();
        requestParam.setFullShortUrl("test.cn/abc");
        requestParam.setOriginUrl("https://example.com");
        requestParam.setGid("g1");
        requestParam.setOriginGid("g0");
        requestParam.setValidDateType(0);
        requestParam.setDescription("desc");

        mockMvc.perform(put("/api/short-link/v1/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestParam)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(shortLinkCoreService).updateShortLink(any(ShortLinkUpdateReqDTO.class));
    }
}
