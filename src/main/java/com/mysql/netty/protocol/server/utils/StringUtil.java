package com.mysql.netty.protocol.server.utils;

import java.io.Serializable;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @Author: yuanhongjun
 * DateTime: 2021-06-11 14:42
 */
public class StringUtil implements Serializable {
    public static final boolean isEmpty(String val) {
        return val == null || val.length() == 0;
    }
}
