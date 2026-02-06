package com.xhy.shortlink.framework.starter.database.algorithm.sharding;

import org.apache.shardingsphere.sharding.api.sharding.standard.PreciseShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.RangeShardingValue;
import org.apache.shardingsphere.sharding.api.sharding.standard.StandardShardingAlgorithm;

import java.util.Collection;
import java.util.Properties;

/**
 * 自定义数据库 Hash 取模分片算法
 * <p>
 * 基于分片键的 hashCode 进行取模运算，将数据均匀分布到各分片表中。
 * 相比 ShardingSphere 内置 HASH_MOD，支持自定义 hash 逻辑和更灵活的配置。
 * <p>
 * YAML 配置示例：
 * <pre>
 * shardingAlgorithms:
 *   custom_hash_mod:
 *     type: CUSTOM_DB_HASH_MOD
 *     props:
 *       sharding-count: 16
 * </pre>
 */
public class CustomDbHashModShardingAlgorithm implements StandardShardingAlgorithm<Comparable<?>> {

    private static final String SHARDING_COUNT_KEY = "sharding-count";

    private int shardingCount;

    private Properties props;

    /**
     * 精确分片：根据分片键值计算目标表
     * <p>
     * 1. 取分片键 hashCode 的绝对值
     * 2. 对分片数量取模得到后缀编号
     * 3. 遍历可用表名，匹配以 "_编号" 结尾的表
     *
     * @param availableTargetNames 所有可用的目标表名集合
     * @param shardingValue        精确分片值（包含逻辑表名、分片键列名、分片键值）
     * @return 匹配的目标表名
     */
    @Override
    public String doSharding(Collection<String> availableTargetNames,
                             PreciseShardingValue<Comparable<?>> shardingValue) {
        int mod = Math.abs(shardingValue.getValue().hashCode()) % shardingCount;
        String suffix = "_" + mod;
        for (String targetName : availableTargetNames) {
            if (targetName.endsWith(suffix)) {
                return targetName;
            }
        }
        throw new IllegalStateException(
                "No available target found for value: " + shardingValue.getValue());
    }

    /**
     * 范围分片：返回所有可用表名（全路由）
     * <p>
     * Hash 取模算法无法根据范围条件精确定位分片，
     * 因此范围查询时退化为全表路由
     *
     * @param availableTargetNames 所有可用的目标表名集合
     * @param shardingValue        范围分片值
     * @return 所有可用表名
     */
    @Override
    public Collection<String> doSharding(Collection<String> availableTargetNames,
                                         RangeShardingValue<Comparable<?>> shardingValue) {
        return availableTargetNames;
    }

    /**
     * 初始化分片算法，从配置中读取分片数量
     *
     * @param props 算法配置属性，必须包含 sharding-count
     */
    @Override
    public void init(Properties props) {
        this.props = props;
        this.shardingCount = Integer.parseInt(
                props.getProperty(SHARDING_COUNT_KEY));
    }

    /**
     * 返回算法的 SPI 类型标识，用于 YAML 配置中的 type 字段
     */
    @Override
    public String getType() {
        return "CUSTOM_DB_HASH_MOD";
    }

}
