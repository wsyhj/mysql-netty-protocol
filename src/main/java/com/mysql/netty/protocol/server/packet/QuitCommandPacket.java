package com.mysql.netty.protocol.server.packet;


import com.mysql.netty.protocol.server.common.Command;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @Author: yuanhongjun
 * DateTime: 2022-01-12 14:42
 */
public class QuitCommandPacket extends CommandPacket {

    private final String someInfo;

    public QuitCommandPacket(int sequenceId, String someInfo) {
        super(sequenceId, Command.COM_QUIT);
        this.someInfo = someInfo;
    }

    public String getSomeInfo() {
        return someInfo;
    }
}
