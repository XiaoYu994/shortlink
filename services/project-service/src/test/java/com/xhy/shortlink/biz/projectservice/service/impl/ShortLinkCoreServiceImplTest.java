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

package com.xhy.shortlink.biz.projectservice.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkPageReqDTO;
import com.xhy.shortlink.biz.api.project.dto.req.ShortLinkUpdateReqDTO;
import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkCreateRespDTO;
import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkGroupCountRespDTO;
import com.xhy.shortlink.biz.api.project.dto.resp.ShortLinkPageRespDTO;
import com.xhy.shortlink.biz.projectservice.common.enums.OrderTagEnum;
import com.xhy.shortlink.biz.projectservice.common.enums.ValidDateTypeEnum;
import com.xhy.shortlink.biz.projectservice.config.GotoDomainWhiteListConfiguration;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkColdDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkDO;
import com.xhy.shortlink.biz.projectservice.dao.entity.ShortLinkGoToDO;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkColdMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkGoToColdMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkGoToMapper;
import com.xhy.shortlink.biz.projectservice.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.biz.projectservice.helper.ShortLinkCacheHelper;
import com.xhy.shortlink.biz.projectservice.metrics.ShortLinkMetrics;
import com.xhy.shortlink.biz.projectservice.mq.producer.ShortLinkCacheProducer;
import com.xhy.shortlink.biz.projectservice.mq.producer.ShortLinkExpireArchiveProducer;
import com.xhy.shortlink.biz.projectservice.mq.producer.ShortLinkRiskProducer;
import com.xhy.shortlink.framework.starter.convention.exception.ClientException;
import com.xhy.shortlink.framework.starter.convention.exception.ServiceException;
import com.xhy.shortlink.framework.starter.designpattern.strategy.AbstractStrategyChoose;
import com.xhy.shortlink.framework.starter.user.core.UserContext;
import com.xhy.shortlink.framework.starter.user.core.UserInfoDTO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.*;

import static com.xhy.shortlink.biz.projectservice.common.constant.RedisKeyConstant.RANK_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShortLinkCoreServiceImplTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, ShortLinkDO.class);
        TableInfoHelper.initTableInfo(assistant, ShortLinkColdDO.class);
        TableInfoHelper.initTableInfo(assistant, ShortLinkGoToDO.class);
    }

    @InjectMocks
    private ShortLinkCoreServiceImpl shortLinkCoreService;

    @Mock
    private ShortLinkMapper shortLinkMapper;
    @Mock
    private ShortLinkGoToMapper shortLinkGoToMapper;
    @Mock
    private ShortLinkColdMapper shortLinkColdMapper;
    @Mock
    private ShortLinkGoToColdMapper shortLinkGoToColdMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ShortLinkRiskProducer riskProducer;
    @Mock
    private ShortLinkCacheProducer cacheProducer;
    @Mock
    private ShortLinkExpireArchiveProducer expireArchiveProducer;
    @Mock
    private ShortLinkCacheHelper cacheHelper;
    @Mock
    private GotoDomainWhiteListConfiguration gotoDomainWhiteListConfiguration;
    @Mock
    private AbstractStrategyChoose abstractStrategyChoose;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private DefaultRedisScript<Long> statsRankMigrateScript;
    @Mock
    private ShortLinkMetrics shortLinkMetrics;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(shortLinkCoreService, "defaultDomain", "test.cn");
        ReflectionTestUtils.setField(shortLinkCoreService, "createStrategy", "bloom_filter");
        UserContext.setUser(UserInfoDTO.builder().userId("12345").username("testuser").build());
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    void createShortLink_success_permanent() {
        ShortLinkCreateReqDTO req = new ShortLinkCreateReqDTO();
        req.setOriginUrl("https://www.example.com");
        req.setGid("gid001");
        req.setCreatedType(0);
        req.setValidDateType(ValidDateTypeEnum.PERMANENT.getType());
        req.setDescription("test link");

        when(gotoDomainWhiteListConfiguration.getEnable()).thenReturn(false);
        when(abstractStrategyChoose.chooseAndExecuteResp(eq("bloom_filter"), anyString()))
                .thenReturn("abc123");

        ShortLinkCreateRespDTO result = shortLinkCoreService.createShortLink(req);

        assertNotNull(result);
        assertEquals("http://test.cn/abc123", result.getFullShortUrl());
        assertEquals("gid001", result.getGid());
        assertEquals("https://www.example.com", result.getOriginUrl());

        verify(shortLinkMapper).insert(any(ShortLinkDO.class));
        verify(shortLinkGoToMapper).insert(any(ShortLinkGoToDO.class));
        verify(cacheHelper).warmUp(eq("test.cn/abc123"), eq("https://www.example.com"), eq("gid001"), isNull());
        verify(riskProducer).sendMessage(any());
        verify(shortLinkMetrics).recordCreateSuccess();
        verify(shortLinkMetrics, never()).recordCreateFailure();
    }

    @Test
    void createShortLink_success_customValidDate() {
        Date futureDate = new Date(System.currentTimeMillis() + 86400000L);
        ShortLinkCreateReqDTO req = new ShortLinkCreateReqDTO();
        req.setOriginUrl("https://www.example.com");
        req.setGid("gid001");
        req.setCreatedType(0);
        req.setValidDateType(ValidDateTypeEnum.CUSTOM.getType());
        req.setValidDate(futureDate);
        req.setDescription("custom date link");

        when(gotoDomainWhiteListConfiguration.getEnable()).thenReturn(false);
        when(abstractStrategyChoose.chooseAndExecuteResp(anyString(), anyString())).thenReturn("xyz789");

        ShortLinkCreateRespDTO result = shortLinkCoreService.createShortLink(req);

        assertNotNull(result);
        verify(expireArchiveProducer).sendMessage(any());
    }

    @Test
    void createShortLink_duplicateKey_throwsServiceException() {
        ShortLinkCreateReqDTO req = new ShortLinkCreateReqDTO();
        req.setOriginUrl("https://www.example.com");
        req.setGid("gid001");
        req.setCreatedType(0);
        req.setValidDateType(ValidDateTypeEnum.PERMANENT.getType());

        when(gotoDomainWhiteListConfiguration.getEnable()).thenReturn(false);
        when(abstractStrategyChoose.chooseAndExecuteResp(anyString(), anyString())).thenReturn("dup001");
        doThrow(new DuplicateKeyException("duplicate")).when(shortLinkMapper).insert(any(ShortLinkDO.class));
        when(cacheHelper.bloomFilterContains(anyString())).thenReturn(false);

        assertThrows(ServiceException.class, () -> shortLinkCoreService.createShortLink(req));
        verify(cacheHelper).addToBloomFilter("test.cn/dup001");
        verify(shortLinkMetrics).recordCreateFailure();
    }

    @Test
    void createShortLink_whitelistBlocked_throwsClientException() {
        ShortLinkCreateReqDTO req = new ShortLinkCreateReqDTO();
        req.setOriginUrl("https://blocked.example.com/page");
        req.setGid("gid001");
        req.setCreatedType(0);
        req.setValidDateType(ValidDateTypeEnum.PERMANENT.getType());

        when(gotoDomainWhiteListConfiguration.getEnable()).thenReturn(true);
        when(gotoDomainWhiteListConfiguration.getDetails()).thenReturn(List.of("allowed.com"));
        when(gotoDomainWhiteListConfiguration.getNames()).thenReturn("Allowed.com");

        assertThrows(ClientException.class, () -> shortLinkCoreService.createShortLink(req));
        verify(shortLinkMetrics).recordCreateFailure();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listGroupShortLinkCount_mergesHotAndCold() {
        List<ShortLinkGroupCountRespDTO> hotCounts = List.of(
                ShortLinkGroupCountRespDTO.builder().gid("g1").shortLinkCount(5).build(),
                ShortLinkGroupCountRespDTO.builder().gid("g2").shortLinkCount(3).build());
        List<ShortLinkGroupCountRespDTO> coldCounts = List.of(
                ShortLinkGroupCountRespDTO.builder().gid("g1").shortLinkCount(2).build());

        when(shortLinkMapper.selectGroupCount(any(LambdaQueryWrapper.class))).thenReturn(hotCounts);
        when(shortLinkColdMapper.selectGroupCount(any(LambdaQueryWrapper.class))).thenReturn(coldCounts);

        List<ShortLinkGroupCountRespDTO> result = shortLinkCoreService.listGroupShortLinkCount(
                List.of("g1", "g2", "g3"));

        assertEquals(3, result.size());
        assertEquals(7, result.stream().filter(r -> "g1".equals(r.getGid())).findFirst().get().getShortLinkCount());
        assertEquals(3, result.stream().filter(r -> "g2".equals(r.getGid())).findFirst().get().getShortLinkCount());
        assertEquals(0, result.stream().filter(r -> "g3".equals(r.getGid())).findFirst().get().getShortLinkCount());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fillTodayStats_withData() {
        ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOps);

        String today = DateUtil.today();
        String fullShortUrl = "test.cn/abc";
        when(zSetOps.score(String.format(RANK_KEY, OrderTagEnum.TODAY_PV.getValue(), "g1", today), fullShortUrl))
                .thenReturn(100D);
        when(zSetOps.score(String.format(RANK_KEY, OrderTagEnum.TODAY_UV.getValue(), "g1", today), fullShortUrl))
                .thenReturn(50D);
        when(zSetOps.score(String.format(RANK_KEY, OrderTagEnum.TODAY_UIP.getValue(), "g1", today), fullShortUrl))
                .thenReturn(30D);

        ShortLinkPageRespDTO dto = new ShortLinkPageRespDTO();
        dto.setGid("g1");
        dto.setFullShortUrl(fullShortUrl);
        shortLinkCoreService.fillTodayStats(dto);

        assertEquals(100, dto.getTodayPv());
        assertEquals(50, dto.getTodayUv());
        assertEquals(30, dto.getTodayUip());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fillTodayStats_emptyData() {
        ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.score(anyString(), anyString())).thenReturn(null);

        ShortLinkPageRespDTO dto = new ShortLinkPageRespDTO();
        dto.setGid("g1");
        dto.setFullShortUrl("test.cn/abc");
        shortLinkCoreService.fillTodayStats(dto);

        assertEquals(0, dto.getTodayPv());
        assertEquals(0, dto.getTodayUv());
        assertEquals(0, dto.getTodayUip());
    }

    @Test
    void updateShortLink_sameGroup_hotData() {
        ShortLinkUpdateReqDTO req = new ShortLinkUpdateReqDTO();
        req.setFullShortUrl("test.cn/abc");
        req.setOriginUrl("https://www.new-url.com");
        req.setGid("g1");
        req.setOriginGid("g1");
        req.setValidDateType(ValidDateTypeEnum.PERMANENT.getType());
        req.setDescription("updated");

        when(gotoDomainWhiteListConfiguration.getEnable()).thenReturn(false);

        ShortLinkDO existing = ShortLinkDO.builder()
                .gid("g1")
                .fullShortUrl("test.cn/abc")
                .originUrl("https://www.old-url.com")
                .enableStatus(0)
                .build();
        when(shortLinkMapper.selectOne(any())).thenReturn(existing);
        when(stringRedisTemplate.delete(anyList())).thenReturn(2L);

        shortLinkCoreService.updateShortLink(req);

        verify(shortLinkMapper).update(
                isNull(),
                any(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class));
        verify(cacheProducer).sendMessage("test.cn/abc");
        verify(riskProducer).sendMessage(any());
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void updateShortLink_notFound_throwsClientException() {
        ShortLinkUpdateReqDTO req = new ShortLinkUpdateReqDTO();
        req.setFullShortUrl("test.cn/notexist");
        req.setOriginUrl("https://www.example.com");
        req.setGid("g1");
        req.setOriginGid("g1");
        req.setValidDateType(ValidDateTypeEnum.PERMANENT.getType());

        when(gotoDomainWhiteListConfiguration.getEnable()).thenReturn(false);
        when(shortLinkMapper.selectOne(any())).thenReturn(null);
        when(shortLinkColdMapper.selectOne(any())).thenReturn(null);

        assertThrows(ClientException.class, () -> shortLinkCoreService.updateShortLink(req));
    }
}
