package com.mysql.netty.protocol.server.packet;


import com.mysql.netty.protocol.server.common.Command;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @Author: yuanhongjun
 * DateTime: 2022-01-12 14:42
 */
public class FieldListCommandPacket extends CommandPacket {

    private final String database;

    public FieldListCommandPacket(int sequenceId, String database) {
        super(sequenceId, Command.COM_FIELD_LIST);
        this.database = database;
    }

    public String getDatabase() {
        if (database != null) {
            return database.trim();
        }
        return null;
    }
}
