package com.xhy.shortlink.project.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhy.shortlink.project.common.convention.exception.ClientException;
import com.xhy.shortlink.project.common.convention.exception.ServiceException;
import com.xhy.shortlink.project.common.enums.ValidDateTypeEnum;
import com.xhy.shortlink.project.dao.entity.ShortLinkDO;
import com.xhy.shortlink.project.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkGroupCountRespDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.xhy.shortlink.project.service.ShortLinkService;
import com.xhy.shortlink.project.toolkit.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/*
* 短链接接口实现层
* */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {
    private final RBloomFilter<String> shortlinkCachePenetrationBloomFilter;
    private final ShortLinkMapper shortLinkMapper;
    @Override
    public ShortLinkCreateRespDTO createShortlink(ShortLinkCreateReqDTO requestParam) {
        String fullShortUrl = requestParam.getDomain() + "/" + generateSuffix(requestParam);
        final ShortLinkDO shortlinkDO = ShortLinkDO.builder()
                .gid(requestParam.getGid())
                .createdType(requestParam.getCreateType())
                .domain(requestParam.getDomain())
                .describe(requestParam.getDescribe())
                .validDateType(requestParam.getValidDateType())
                .validDate(requestParam.getValidDate())
                .fullShortUrl(fullShortUrl)
                .originUrl(requestParam.getOriginUrl())
                .shortUri(generateSuffix(requestParam))
                .build();
        // 如果发生布隆冲突，就说明短链接重复了，这个时候数据库就会报key冲突 之前设置了 唯一索引 full_short_url
        try {
            baseMapper.insert(shortlinkDO);
        } catch (DuplicateKeyException e) {
            log.warn("短链接：{} 重复入库",fullShortUrl);
            throw new ServiceException("短链接：" + fullShortUrl + " 已存在");
        }
        // 短链接没有问题就将这个短链接加入布隆过滤器
        shortlinkCachePenetrationBloomFilter.add(shortlinkDO.getShortUri());
        return ShortLinkCreateRespDTO.builder()
                .fullShortUrl(shortlinkDO.getFullShortUrl())
                .gid(shortlinkDO.getGid())
                .originUrl(requestParam.getOriginUrl())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShortlink(ShortLinkUpdateReqDTO requestParam) {
        // 使用 originGid (旧分组ID) 去查
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getOriginGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getEnableStatus, 0);

        ShortLinkDO shortLinkDO = baseMapper.selectOne(queryWrapper);
        if (shortLinkDO == null) {
            throw new ClientException("短链接记录不存在");
        }

        // 2. 判断分组是否改变
        if (Objects.equals(requestParam.getOriginGid(), requestParam.getGid())) {
            // === 2.1：分组没变，原地更新 ===
            LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                    .eq(ShortLinkDO::getGid, requestParam.getGid())
                    .set(ShortLinkDO::getOriginUrl, requestParam.getOriginUrl())
                    .set(ShortLinkDO::getDescribe, requestParam.getDescribe())
                    .set(ShortLinkDO::getValidDateType, requestParam.getValidDateType())
                    .set(ShortLinkDO::getValidDate,
                            Objects.equals(requestParam.getValidDateType(), ValidDateTypeEnum.PERMANENT.getType()) ? null : requestParam.getValidDate());
            baseMapper.update(null, updateWrapper); // 记得执行！

        } else {
            // === 情况 2.1：分组改变，先删后插 ===
            // 2.1.1 删除旧数据 物理删除
            baseMapper.deletePhysical(requestParam.getOriginGid(),requestParam.getFullShortUrl());
            // 2.2.2 准备新数据 (直接修改查出来的对象，保留了 clickNum 等历史数据)
            shortLinkDO.setGid(requestParam.getGid()); // 设置新分组
            shortLinkDO.setOriginUrl(requestParam.getOriginUrl());
            shortLinkDO.setDescribe(requestParam.getDescribe());
            shortLinkDO.setValidDateType(requestParam.getValidDateType());
            // 处理有效期逻辑
            shortLinkDO.setValidDate( Objects.equals(requestParam.getValidDateType(), ValidDateTypeEnum.PERMANENT.getType()) ? null : requestParam.getValidDate());
            shortLinkDO.setId(null); // ID置空，让数据库重新生成
            baseMapper.insert(shortLinkDO);
        }

    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortlink(ShortLinkPageReqDTO requestParam) {
        final LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getEnableStatus,0)
                .orderByDesc(ShortLinkDO::getCreateTime);
        final IPage<ShortLinkDO>  resultPage = baseMapper.selectPage(requestParam, queryWrapper);
        return resultPage.convert(each -> BeanUtil.toBean(each, ShortLinkPageRespDTO.class));
    }

    @Override
    public List<ShortLinkGroupCountRespDTO> listGroupShortlinkCount(List<String> requestParam) {
        // 构造条件 (只写 Where 和 GroupBy 部分)
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getEnableStatus, 0)
                .in(ShortLinkDO::getGid, requestParam)
                .groupBy(ShortLinkDO::getGid);
        return shortLinkMapper.selectGroupCount(queryWrapper);
    }

    /*
    * 生成短链接后缀
    * */
    private String generateSuffix(ShortLinkCreateReqDTO requestParam) {
        int customGenerateCount = 0;
        String shortUri;
        while (true) {
            if (customGenerateCount > 10) {
                throw new ServiceException("短链接生成频繁，请稍后再试");
            }
            String originUrl = requestParam.getOriginUrl();
            // 使用UUID避免返回的短链接重复，降低这个重复的概率
            originUrl += UUID.randomUUID().toString();
            shortUri = HashUtil.hashToBase62(originUrl);
            if(!shortlinkCachePenetrationBloomFilter.contains(requestParam.getDomain() + "/" + shortUri)){
                break;
            }
                customGenerateCount++;
        }
        return shortUri;
    }
}
