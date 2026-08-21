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

package com.xhy.shortlink.biz.statsservice.locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.xhy.shortlink.biz.statsservice.common.constant.RedisKeyConstant.LOCALE_IP_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IpLocationServiceTest {

    @InjectMocks
    private IpLocationService ipLocationService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ipLocationService, "statsLocaleAmapKey", "test-key");
    }

    @Test
    void peekWithoutHttp_privateIp_isUnknownWithoutRedis() {
        Optional<IpLocation> location = ipLocationService.peekWithoutHttp("172.29.0.1");
        assertTrue(location.isPresent());
        assertTrue(location.get().isUnknown());
        verify(stringRedisTemplate, never()).opsForValue();
    }

    @Test
    void peekWithoutHttp_loopback_isUnknown() {
        assertTrue(ipLocationService.peekWithoutHttp("127.0.0.1").orElseThrow().isUnknown());
    }

    @Test
    void peekWithoutHttp_cacheHit_returnsCached() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(String.format(LOCALE_IP_KEY, "8.8.8.8")))
                .thenReturn("广东省|深圳市|440300");
        Optional<IpLocation> location = ipLocationService.peekWithoutHttp("8.8.8.8");
        assertEquals("广东省", location.orElseThrow().province());
        assertEquals("深圳市", location.orElseThrow().city());
    }

    @Test
    void peekWithoutHttp_publicUncached_empty() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        assertTrue(ipLocationService.peekWithoutHttp("8.8.8.8").isEmpty());
    }

    @Test
    void resolveRemote_blankKey_unknownWithoutHttp() {
        ReflectionTestUtils.setField(ipLocationService, "statsLocaleAmapKey", "");
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        assertTrue(ipLocationService.resolveRemote("8.8.8.8").isUnknown());
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), eq(TimeUnit.HOURS));
    }

    @Test
    void isPrivateOrLocal_coversRfc1918AndCgnat() {
        assertTrue(IpLocationService.isPrivateOrLocal("10.0.0.1"));
        assertTrue(IpLocationService.isPrivateOrLocal("192.168.1.1"));
        assertTrue(IpLocationService.isPrivateOrLocal("172.16.0.1"));
        assertTrue(IpLocationService.isPrivateOrLocal("100.64.1.1"));
        assertTrue(IpLocationService.isPrivateOrLocal("::1"));
        assertTrue(IpLocationService.isPrivateOrLocal("not-an-ip"));
        assertTrue(IpLocationService.isLiteralIpv4("8.8.8.8"));
        assertTrue(!IpLocationService.isPrivateOrLocal("8.8.8.8"));
    }

    @Test
    void peekWithoutHttp_malformedCache_empty() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("only-one-part");
        assertTrue(ipLocationService.peekWithoutHttp("8.8.8.8").isEmpty());
    }

    @Test
    void parseAmapBody_successAndEmptyArray() {
        IpLocation ok = IpLocationService.parseAmapBody(
                "{\"infocode\":\"10000\",\"province\":\"湖南省\",\"city\":\"长沙市\",\"adcode\":\"430100\"}");
        assertEquals("湖南省", ok.province());
        assertEquals("长沙市", ok.city());
        assertTrue(IpLocationService.parseAmapBody(
                "{\"infocode\":\"10000\",\"province\":\"[]\",\"city\":\"[]\",\"adcode\":\"[]\"}").isUnknown());
        assertTrue(IpLocationService.parseAmapBody("{\"infocode\":\"10021\"}").isUnknown());
        assertTrue(IpLocationService.parseAmapBody(null).isUnknown());
    }
}
