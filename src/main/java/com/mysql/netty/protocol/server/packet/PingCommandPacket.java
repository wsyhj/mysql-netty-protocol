package com.mysql.netty.protocol.server.packet;

import com.mysql.netty.protocol.server.common.Command;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @Author: yuanhongjun
 * DateTime: 2022-01-12 14:42
 */
public class PingCommandPacket extends CommandPacket {

    private final String pingInfo;

    public PingCommandPacket(int sequenceId, String someInfo) {
        super(sequenceId, Command.COM_PING);
        this.pingInfo = someInfo;
    }

    public String getPingInfo() {
        return pingInfo;
    }
}
