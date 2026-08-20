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

package com.xhy.shortlink.biz.projectservice.toolkit;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaviconServiceTest {

    private final FaviconService faviconService = new FaviconService();

    @Test
    void guessRootIcon_usesHostAndScheme() {
        assertEquals("https://www.baidu.com/favicon.ico",
                faviconService.guessRootIcon("https://www.baidu.com/s?wd=1"));
        assertEquals("http://localhost:8080/favicon.ico",
                faviconService.guessRootIcon("http://localhost:8080/path"));
        assertNull(faviconService.guessRootIcon("not-a-url"));
    }

    @Test
    void calculateIconScore_prefersAppleTouchAndLargeSizes() {
        Element apple = Jsoup.parse("<link rel=\"apple-touch-icon\" sizes=\"192x192\" href=\"/a.png\">")
                .selectFirst("link");
        Element plain = Jsoup.parse("<link rel=\"icon\" href=\"/b.ico\">").selectFirst("link");
        assert apple != null;
        assert plain != null;
        assertTrue(faviconService.calculateIconScore(apple) > faviconService.calculateIconScore(plain));
    }
}
