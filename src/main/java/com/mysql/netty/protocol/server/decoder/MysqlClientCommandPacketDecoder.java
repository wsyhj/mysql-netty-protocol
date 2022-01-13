package com.mysql.netty.protocol.server.decoder;


import com.mysql.netty.protocol.server.common.Command;
import com.mysql.netty.protocol.server.common.Constants;
import com.mysql.netty.protocol.server.common.MysqlCharacterSet;
import com.mysql.netty.protocol.server.packet.*;
import com.mysql.netty.protocol.server.utils.CodecUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;

import java.util.List;
import java.util.Optional;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @Author: yuanhongjun
 * DateTime: 2022-01-12 14:42
 */
public class MysqlClientCommandPacketDecoder extends AbstractPacketDecoder implements MysqlClientPacketDecoder {

    public MysqlClientCommandPacketDecoder() {
        this(Constants.DEFAULT_MAX_PACKET_SIZE);
    }

    public MysqlClientCommandPacketDecoder(int maxPacketSize) {
        super(maxPacketSize);
    }

    @Override
    protected void decodePacket(ChannelHandlerContext ctx, int sequenceId, ByteBuf packet, List<Object> out) {
        final MysqlCharacterSet clientCharset = MysqlCharacterSet.getClientCharsetAttr(ctx.channel());

        final byte commandCode = packet.readByte();
        final Optional<Command> command = Command.findByCommandCode(commandCode);
        if (!command.isPresent()) {
            throw new DecoderException("Unknown command " + commandCode);
        }
        switch (command.get()) {
            case COM_QUERY:
                out.add(new QueryCommandPacket(sequenceId, CodecUtils.readFixedLengthString(packet, packet.readableBytes(), clientCharset.getCharset())));
                break;
            case COM_FIELD_LIST:
                out.add(new FieldListCommandPacket(sequenceId, CodecUtils.readFixedLengthString(packet, packet.readableBytes(), clientCharset.getCharset())));
                break;
            case COM_INIT_DB:
                out.add(new UseDBCommandPacket(sequenceId, CodecUtils.readFixedLengthString(packet, packet.readableBytes(), clientCharset.getCharset())));
                break;
            case COM_QUIT:
                out.add(new QuitCommandPacket(sequenceId, CodecUtils.readFixedLengthString(packet, packet.readableBytes(), clientCharset.getCharset())));
                break;
            case COM_PING:
                out.add(new PingCommandPacket(sequenceId, CodecUtils.readFixedLengthString(packet, packet.readableBytes(), clientCharset.getCharset())));
                break;
            default:
                out.add(new CommandPacket(sequenceId, command.get()));
        }
    }
}
