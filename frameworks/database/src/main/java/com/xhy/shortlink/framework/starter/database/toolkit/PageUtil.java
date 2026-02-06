package com.xhy.shortlink.framework.starter.database.toolkit;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xhy.shortlink.framework.stater.convention.page.PageRequest;
import com.xhy.shortlink.framework.stater.convention.page.PageResponse;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页工具类
 * <p>
 * 负责 convention 层分页对象与 MyBatis-Plus 分页对象之间的转换，
 * 作为防腐层隔离底层 ORM 框架的分页实现细节
 */
public final class PageUtil {

    private PageUtil() {
    }

    /**
     * 将 convention 层的 PageRequest 转换为 MyBatis-Plus 的 Page 对象
     *
     * @param pageRequest 分页请求参数
     * @param <T>         实体类型
     * @return MyBatis-Plus 分页对象
     */
    public static <T> Page<T> convert(PageRequest pageRequest) {
        return convert(pageRequest.getCurrent(), pageRequest.getSize());
    }

    /**
     * 根据页码和每页大小创建 MyBatis-Plus 的 Page 对象
     *
     * @param current 当前页码
     * @param size    每页大小
     * @param <T>     实体类型
     * @return MyBatis-Plus 分页对象
     */
    public static <T> Page<T> convert(long current, long size) {
        return new Page<>(current, size);
    }

    /**
     * 将 MyBatis-Plus 的 IPage 查询结果转换为 convention 层的 PageResponse
     *
     * @param iPage MyBatis-Plus 分页查询结果
     * @param <T>   实体类型
     * @return convention 层分页响应对象
     */
    public static <T> PageResponse<T> convert(IPage<T> iPage) {
        return PageResponse.<T>builder()
                .current(iPage.getCurrent())
                .size(iPage.getSize())
                .total(iPage.getTotal())
                .records(iPage.getRecords())
                .build();
    }

    /**
     * 将 MyBatis-Plus 的 IPage 查询结果转换为 PageResponse，同时对记录做类型映射
     * <p>
     * 典型场景：DO 分页结果转换为 VO 分页结果
     *
     * @param iPage  MyBatis-Plus 分页查询结果
     * @param mapper DO 到 VO 的转换函数
     * @param <S>    源类型（DO）
     * @param <T>    目标类型（VO）
     * @return 转换后的分页响应对象
     */
    public static <S, T> PageResponse<T> convert(IPage<S> iPage, Function<? super S, ? extends T> mapper) {
        List<T> targetRecords = iPage.getRecords().stream()
                .map(mapper)
                .collect(Collectors.toList());
        return PageResponse.<T>builder()
                .current(iPage.getCurrent())
                .size(iPage.getSize())
                .total(iPage.getTotal())
                .records(targetRecords)
                .build();
    }
}
