package com.mysql.netty.protocol.server.context;


import com.mysql.netty.protocol.server.utils.StringUtil;
import io.netty.channel.ChannelHandlerContext;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @Author: yuanhongjun
 * DateTime: 2022-01-12 14:42
 */
public class ConnectContext implements Serializable {
    private ChannelHandlerContext ctx;
    private String currentDatabase;
    private Map<String, String> properties = new HashMap<>();
    private Map<String, String> settingSQLs = new HashMap<>();


    public String getCurrentDatabase() {
        if (StringUtil.isEmpty(this.currentDatabase)) {
            return "";
        }

        return currentDatabase;
    }

    public void setCurrentDatabase(String currentDatabase) {
        this.currentDatabase = currentDatabase;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public ChannelHandlerContext getCtx() {
        return ctx;
    }

    public void setCtx(ChannelHandlerContext ctx) {
        this.ctx = ctx;
    }

    public Map<String, String> getSettingSQLs() {
        return settingSQLs;
    }

    public void setSettingSQL(String key, String value) {
        this.settingSQLs.put(key, value);
    }

    public void setSettingSQLs(Map<String, String> settingSQLs) {
        this.settingSQLs = settingSQLs;
    }

}
