package com.mysql.netty.protocol.server.packet;

import com.mysql.netty.protocol.server.common.Command;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @Author: yuanhongjun
 * DateTime: 2022-01-12 14:42
 */
public class SetCommandPacket extends CommandPacket {
    private String setting;

    public SetCommandPacket(int sequenceId, String setting) {
        super(sequenceId, Command.COM_SET_OPTION);
        this.setting = setting;
    }

    public String getSetting() {
        return setting;
    }

    public void setSetting(String setting) {
        this.setting = setting;
    }
}
