package com.mysql.netty.protocol.server.result;

import java.io.Serializable;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @Author: yuanhongjun
 * DateTime: 2022-01-06 15:58
 */
public class QueryResultSchema implements Serializable {
    private String field;
    private String type;

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
