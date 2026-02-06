package com.xhy.shortlink.framework.starter.bases.safa;

import org.springframework.beans.factory.InitializingBean;

/*
*  开启 FastJSON2 的安全模式，防止反序列化漏洞
*    - 关闭 FastJSON 的 autoType 功能
     - 防止通过 @type 字段进行反序列化攻击
     - 避免 RCE（远程代码执行）漏洞
* */
public class FastJsonSafeMode implements InitializingBean {

    /*
    *    配置方式:
          # application.yml
          framework:
            fastjson:
              safe-mode: true  # 开启安全模式
    * */
    @Override
    public void afterPropertiesSet() throws Exception {
        System.setProperty("fastjson2.parser.safeMode", "true");
    }
}
