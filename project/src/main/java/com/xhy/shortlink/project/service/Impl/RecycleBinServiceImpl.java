package com.xhy.shortlink.project.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhy.shortlink.project.common.convention.exception.ClientException;
import com.xhy.shortlink.project.common.enums.LinkEnableStatusEnum;
import com.xhy.shortlink.project.dao.entity.ShortLinkColdDO;
import com.xhy.shortlink.project.dao.entity.ShortLinkDO;
import com.xhy.shortlink.project.dao.mapper.ShortLinkColdMapper;
import com.xhy.shortlink.project.dao.mapper.ShortLinkMapper;
import com.xhy.shortlink.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkRecycleBinRecoverReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkRecycleBinRemoveReqDTO;
import com.xhy.shortlink.project.dto.req.ShortLinkRecycleBinSaveReqDTO;
import com.xhy.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.xhy.shortlink.project.service.RecycleBinService;
import com.xhy.shortlink.project.service.ShortLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.GOTO_IS_NULL_SHORT_LINK_KEY;
import static com.xhy.shortlink.project.common.constant.RedisKeyConstant.GOTO_SHORT_LINK_KEY;

/*
* 回收站接口实现层
* */
@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements RecycleBinService {
    private final StringRedisTemplate stringRedisTemplate;
    private final ShortLinkService shortLinkService;
    private final ShortLinkColdMapper shortLinkColdMapper;

    @Override
    public void recycleBinSave(ShortLinkRecycleBinSaveReqDTO requestParam) {
        final LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getEnableStatus());
        baseMapper.update(ShortLinkDO.builder().enableStatus(LinkEnableStatusEnum.NOT_ENABLED.getEnableStatus()).build(), updateWrapper);
        // 还需要删除对应的缓存
        stringRedisTemplate.delete(String.format(GOTO_SHORT_LINK_KEY, requestParam.getFullShortUrl()));
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortlink(ShortLinkRecycleBinPageReqDTO requestParam) {
        long current = requestParam.getCurrent();
        long size = requestParam.getSize();
        long need = current * size;

        // 热库：放大分页以支持热+冷合并后再切片
        ShortLinkRecycleBinPageReqDTO hotReq = new ShortLinkRecycleBinPageReqDTO();
        hotReq.setCurrent(1);
        hotReq.setSize(need);
        hotReq.setOrderTag(requestParam.getOrderTag());
        hotReq.setGidList(requestParam.getGidList());
        IPage<ShortLinkDO> hotPage = baseMapper.pageRecycleBinLink(hotReq);

        // 冷库：按相同条件查询
        LambdaQueryWrapper<ShortLinkColdDO> coldWrapper = Wrappers.lambdaQuery(ShortLinkColdDO.class)
                .in(ShortLinkColdDO::getEnableStatus,
                        LinkEnableStatusEnum.NOT_ENABLED.getEnableStatus(),
                        LinkEnableStatusEnum.BANNED.getEnableStatus())
                .eq(ShortLinkColdDO::getDelFlag, 0);
        if (requestParam.getGidList() != null && !requestParam.getGidList().isEmpty()) {
            coldWrapper.in(ShortLinkColdDO::getGid, requestParam.getGidList());
        }
        applyRecycleOrder(coldWrapper, requestParam.getOrderTag());
        Page<ShortLinkColdDO> coldPage = new Page<>(1, need);
        List<ShortLinkColdDO> coldList = shortLinkColdMapper.selectPage(coldPage, coldWrapper).getRecords();

        List<ShortLinkPageRespDTO> merged = new ArrayList<>();
        hotPage.getRecords().forEach(each -> {
            final ShortLinkPageRespDTO result = BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
            result.setDomain("http://" + each.getDomain());
            shortLinkService.fillTodayStats(result);
            merged.add(result);
        });
        coldList.forEach(each -> {
            final ShortLinkPageRespDTO result = BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
            result.setDomain("http://" + each.getDomain());
            shortLinkService.fillTodayStats(result);
            merged.add(result);
        });

        merged.sort(buildRecycleComparator(requestParam.getOrderTag()));
        int fromIndex = (int) ((current - 1) * size);
        int toIndex = (int) Math.min(fromIndex + size, merged.size());
        List<ShortLinkPageRespDTO> pageRecords = fromIndex >= merged.size()
                ? new ArrayList<>()
                : merged.subList(fromIndex, toIndex);

        Page<ShortLinkPageRespDTO> mergedPage = new Page<>();
        mergedPage.setRecords(pageRecords);
        mergedPage.setTotal(hotPage.getTotal() + coldPage.getTotal());
        mergedPage.setCurrent(current);
        mergedPage.setSize(size);
        return mergedPage;
    }

    private void applyRecycleOrder(LambdaQueryWrapper<ShortLinkColdDO> wrapper, String orderTag) {
        if ("totalPv".equals(orderTag)) {
            wrapper.orderByDesc(ShortLinkColdDO::getTotalPv);
        } else if ("totalUv".equals(orderTag)) {
            wrapper.orderByDesc(ShortLinkColdDO::getTotalUv);
        } else if ("totalUip".equals(orderTag)) {
            wrapper.orderByDesc(ShortLinkColdDO::getTotalUip);
        } else {
            wrapper.orderByDesc(ShortLinkColdDO::getCreateTime);
        }
    }

    private Comparator<ShortLinkPageRespDTO> buildRecycleComparator(String orderTag) {
        if ("totalPv".equals(orderTag)) {
            return Comparator.comparing((ShortLinkPageRespDTO dto) -> dto.getTotalPv() == null ? 0 : dto.getTotalPv()).reversed()
                    .thenComparing(ShortLinkPageRespDTO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        if ("totalUv".equals(orderTag)) {
            return Comparator.comparing((ShortLinkPageRespDTO dto) -> dto.getTotalUv() == null ? 0 : dto.getTotalUv()).reversed()
                    .thenComparing(ShortLinkPageRespDTO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        if ("totalUip".equals(orderTag)) {
            return Comparator.comparing((ShortLinkPageRespDTO dto) -> dto.getTotalUip() == null ? 0 : dto.getTotalUip()).reversed()
                    .thenComparing(ShortLinkPageRespDTO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()));
        }
        return Comparator.comparing(ShortLinkPageRespDTO::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    @Override
    public void recoverShortlink(ShortLinkRecycleBinRecoverReqDTO requestParam) {
        if (requestParam.getEnableStatus() == LinkEnableStatusEnum.BANNED.getEnableStatus()) {
            throw new ClientException("短链接被封禁，无法恢复，请联系客服解封后重试");
        }
        final LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getEnableStatus, LinkEnableStatusEnum.NOT_ENABLED.getEnableStatus());
        baseMapper.update(ShortLinkDO.builder().enableStatus(LinkEnableStatusEnum.ENABLE.getEnableStatus()).build(), updateWrapper);
        // 删除对应的缓存 空值
        stringRedisTemplate.delete(String.format(GOTO_IS_NULL_SHORT_LINK_KEY, requestParam.getFullShortUrl()));
    }

    @Override
    public void removeShortlink(ShortLinkRecycleBinRemoveReqDTO requestParam) {
        final LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .in(ShortLinkDO::getEnableStatus,
                        LinkEnableStatusEnum.NOT_ENABLED.getEnableStatus(),
                        LinkEnableStatusEnum.BANNED.getEnableStatus());
        baseMapper.delete(queryWrapper);
    }
}
