package com.xhy.shortlink.project.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xhy.shortlink.project.common.convention.exception.ClientException;
import com.xhy.shortlink.project.common.enums.LinkEnableStatusEnum;
import com.xhy.shortlink.project.dao.entity.ShortLinkDO;
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
        IPage<ShortLinkDO>  resultPage = baseMapper.pageRecycleBinLink(requestParam);
        return resultPage.convert(each -> {
            final ShortLinkPageRespDTO result = BeanUtil.toBean(each, ShortLinkPageRespDTO.class);
            result.setDomain("http://" + each.getDomain());
            shortLinkService.fillTodayStats(result);
            return result;
        });
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
                .ne(ShortLinkDO::getEnableStatus, LinkEnableStatusEnum.ENABLE.getEnableStatus()); // 不等于启用的都可以删除
        baseMapper.delete(queryWrapper);
    }
}
