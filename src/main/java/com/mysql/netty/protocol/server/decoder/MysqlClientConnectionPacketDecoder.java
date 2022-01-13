package com.mysql.netty.protocol.server.decoder;


import com.mysql.netty.protocol.server.common.CapabilityFlags;
import com.mysql.netty.protocol.server.common.Constants;
import com.mysql.netty.protocol.server.common.MysqlCharacterSet;
import com.mysql.netty.protocol.server.packet.HandshakeResponsePacket;
import com.mysql.netty.protocol.server.utils.CodecUtils;
import com.mysql.netty.protocol.server.utils.StringUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;

import java.util.EnumSet;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @Author: yuanhongjun
 * DateTime: 2022-01-12 14:42
 */
public class MysqlClientConnectionPacketDecoder extends AbstractPacketDecoder implements MysqlClientPacketDecoder {

    public MysqlClientConnectionPacketDecoder() {
        this(DEFAULT_MAX_PACKET_SIZE);
    }

    public MysqlClientConnectionPacketDecoder(int maxPacketSize) {
        super(maxPacketSize);
    }

    @Override
    protected void decodePacket(ChannelHandlerContext ctx, int sequenceId, ByteBuf packet, List<Object> out) {
        final EnumSet<CapabilityFlags> clientCapabilities = CodecUtils.readIntEnumSet(packet, CapabilityFlags.class);

        if (!clientCapabilities.contains(CapabilityFlags.CLIENT_PROTOCOL_41)) {
            throw new DecoderException("MySQL client protocol 4.1 support required");
        }

        final HandshakeResponsePacket.Builder response = HandshakeResponsePacket.create();
        response.addCapabilities(clientCapabilities)
                .maxPacketSize((int) packet.readUnsignedIntLE());
        final MysqlCharacterSet characterSet = MysqlCharacterSet.findById(packet.readByte());
        response.characterSet(characterSet);
        response.authPluginName(Constants.MYSQL_NATIVE_PASSWORD);
        packet.skipBytes(23);//unused编码
        if (packet.isReadable()) {
            response.username(CodecUtils.readNullTerminatedString(packet, characterSet.getCharset()));

            final EnumSet<CapabilityFlags> serverCapabilities = CapabilityFlags.getCapabilitiesAttr(ctx.channel());
            final EnumSet<CapabilityFlags> capabilities = EnumSet.copyOf(clientCapabilities);
            capabilities.retainAll(serverCapabilities);

            final int authResponseLength;
            if (capabilities.contains(CapabilityFlags.CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA)) {
                authResponseLength = (int) CodecUtils.readLengthEncodedInteger(packet);
            } else if (capabilities.contains(CapabilityFlags.CLIENT_SECURE_CONNECTION)) {
                authResponseLength = packet.readUnsignedByte();
            } else {
                authResponseLength = CodecUtils.findNullTermLen(packet);
            }
            response.addAuthData(packet, authResponseLength);

            if (capabilities.contains(CapabilityFlags.CLIENT_CONNECT_WITH_DB)) {
                response.database(CodecUtils.readNullTerminatedString(packet, characterSet.getCharset()));
            }

            if (capabilities.contains(CapabilityFlags.CLIENT_PLUGIN_AUTH)) {
                response.authPluginName(CodecUtils.readNullTerminatedString(packet, characterSet.getCharset()));
            }

            if (capabilities.contains(CapabilityFlags.CLIENT_CONNECT_ATTRS)) {
                final long keyValueLen = CodecUtils.readLengthEncodedInteger(packet);
                for (int i = 0; i < keyValueLen; ) {
                    String key = CodecUtils.readLengthEncodedString(packet, characterSet.getCharset());
                    String value = CodecUtils.readLengthEncodedString(packet, characterSet.getCharset());
                    int keyLen = key.length();
                    if (StringUtil.isEmpty(key)) {
                        keyLen = 1;
                    }

                    int valLen = key.length();
                    if (StringUtil.isEmpty(value)) {
                        valLen = 1;
                    }

                    i += keyLen + valLen;

                    response.addAttribute(key, value);
                }
            }
        }
        out.add(response.build());
    }
}
