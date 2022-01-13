package com.mysql.netty.protocol.server.packet;


import com.mysql.netty.protocol.server.common.Command;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @Author: yuanhongjun
 * DateTime: 2022-01-12 14:42
 */
public class UseDBCommandPacket extends CommandPacket {

    private final String database;

    public UseDBCommandPacket(int sequenceId, String database) {
        super(sequenceId, Command.COM_INIT_DB);
        this.database = database;
    }

    public String getDatabase() {
        if (database != null) {
            return database.trim();
        }
        return null;
    }
}
