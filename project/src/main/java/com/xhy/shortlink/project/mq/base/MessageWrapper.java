package com.xhy.shortlink.project.mq.base;

import lombok.*;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor(force = true)
@AllArgsConstructor
@RequiredArgsConstructor
public class MessageWrapper<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    /*
     *  消息发送 keys
     * */
    @NonNull
    private String keys;

    /*
     *  消息体
     * */
    @NonNull
    private T message;

    /*
     *  消息发送时间
     * */
    private Long timestamp = System.currentTimeMillis();
}
