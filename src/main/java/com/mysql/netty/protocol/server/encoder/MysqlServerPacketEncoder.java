/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.mysql.netty.protocol.server.encoder;


import com.mysql.netty.protocol.server.common.CapabilityFlags;
import com.mysql.netty.protocol.server.common.Constants;
import com.mysql.netty.protocol.server.common.MysqlCharacterSet;
import com.mysql.netty.protocol.server.common.ServerStatusFlag;
import com.mysql.netty.protocol.server.packet.*;
import com.mysql.netty.protocol.server.packet.EofResponsePacket;
import com.mysql.netty.protocol.server.packet.ErrorResponsePacket;
import com.mysql.netty.protocol.server.packet.OkResponsePacket;
import com.mysql.netty.protocol.server.utils.CodecUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;

import java.nio.charset.Charset;
import java.util.EnumSet;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @Author: yuanhongjun
 * DateTime: 2022-01-12 14:42
 */
public class MysqlServerPacketEncoder extends AbstractPacketEncoder<MysqlServerPacket> {

    @Override
    protected void encodePacket(ChannelHandlerContext ctx, MysqlServerPacket packet, ByteBuf buf) {
        final EnumSet<CapabilityFlags> capabilities = CapabilityFlags.getCapabilitiesAttr(ctx.channel());
        final Charset serverCharset = MysqlCharacterSet.getServerCharsetAttr(ctx.channel()).getCharset();
        if (packet instanceof ColumnCountPacket) {
            encodeColumnCount((ColumnCountPacket) packet, buf);
        } else if (packet instanceof ColumnDefinitionPacket) {
            encodeColumnDefinition(serverCharset, (ColumnDefinitionPacket) packet, buf);
        } else if (packet instanceof EofResponsePacket) {
            encodeEofResponse(capabilities, (EofResponsePacket) packet, buf);
        } else if (packet instanceof HandshakePacket) {
            encodeHandshake((HandshakePacket) packet, buf);
        } else if (packet instanceof OkResponsePacket) {
            encodeOkResponse(capabilities, serverCharset, (OkResponsePacket) packet, buf);
        } else if (packet instanceof ResultsetRowPacket) {
            encodeResultsetRow(serverCharset, (ResultsetRowPacket) packet, buf);
        } else if (packet instanceof ErrorResponsePacket) {
            encodeErrorResponse(capabilities, (ErrorResponsePacket) packet, buf);
        } else {
            throw new IllegalStateException("Unknown packet type: " + packet.getClass());
        }
    }

    protected void encodeColumnCount(ColumnCountPacket columnCount, ByteBuf buf) {
        CodecUtils.writeLengthEncodedInt(buf, (long) columnCount.getFieldCount());
    }

    protected void encodeColumnDefinition(Charset serverCharset, ColumnDefinitionPacket packet, ByteBuf buf) {
        CodecUtils.writeLengthEncodedString(buf, packet.getCatalog(), serverCharset);
        CodecUtils.writeLengthEncodedString(buf, packet.getSchema(), serverCharset);
        CodecUtils.writeLengthEncodedString(buf, packet.getTable(), serverCharset);
        CodecUtils.writeLengthEncodedString(buf, packet.getOrgTable(), serverCharset);
        CodecUtils.writeLengthEncodedString(buf, packet.getName(), serverCharset);
        CodecUtils.writeLengthEncodedString(buf, packet.getOrgName(), serverCharset);
        buf.writeByte(0x0c);
        buf.writeShortLE(packet.getCharacterSet().getId())
                .writeIntLE((int) packet.getColumnLength())
                .writeByte(packet.getType().getValue())
                .writeShortLE((int) CodecUtils.toLong(packet.getFlags()))
                .writeByte(packet.getDecimals())
                .writeShort(0);
    }

    protected void encodeEofResponse(EnumSet<CapabilityFlags> capabilities, EofResponsePacket eof, ByteBuf buf) {
        buf.writeByte(0xfe);
        if (capabilities.contains(CapabilityFlags.CLIENT_PROTOCOL_41)) {
            buf.writeShortLE(eof.getWarnings())
                    .writeShortLE((int) CodecUtils.toLong(eof.getStatusFlags()));
        }
    }

    protected void encodeHandshake(HandshakePacket handshake, ByteBuf buf) {
        buf.writeByte(handshake.getProtocolVersion())
                .writeBytes(handshake.getServerVersion().array())
                .writeByte(Constants.NUL_BYTE)
                .writeIntLE(handshake.getConnectionId())
                .writeBytes(handshake.getAuthPluginData(), Constants.AUTH_PLUGIN_DATA_PART1_LEN)
                .writeByte(Constants.NUL_BYTE)
                .writeShortLE((int) CodecUtils.toLong(handshake.getCapabilities()))
                .writeByte(handshake.getCharacterSet().getId())
                .writeShortLE((int) CodecUtils.toLong(handshake.getServerStatus()))
                .writeShortLE((int) (CodecUtils.toLong(handshake.getCapabilities()) >> Short.SIZE));
        if (handshake.getCapabilities().contains(CapabilityFlags.CLIENT_PLUGIN_AUTH)) {
            buf.writeByte(handshake.getAuthPluginData().readableBytes() + Constants.AUTH_PLUGIN_DATA_PART1_LEN);
        } else {
            buf.writeByte(Constants.NUL_BYTE);
        }
        buf.writeZero(Constants.HANDSHAKE_RESERVED_BYTES);
        if (handshake.getCapabilities().contains(CapabilityFlags.CLIENT_SECURE_CONNECTION)) {
            final int padding = Constants.AUTH_PLUGIN_DATA_PART2_MIN_LEN - handshake.getAuthPluginData().readableBytes();
            buf.writeBytes(handshake.getAuthPluginData());
            if (padding > 0) {
                buf.writeZero(padding);
            }
        }
        if (handshake.getCapabilities().contains(CapabilityFlags.CLIENT_PLUGIN_AUTH)) {
            ByteBufUtil.writeUtf8(buf, handshake.getAuthPluginName());
            buf.writeByte(Constants.NUL_BYTE);
        }
    }

    protected void encodeOkResponse(EnumSet<CapabilityFlags> capabilities, Charset serverCharset, OkResponsePacket response, ByteBuf buf) {
        buf.writeByte(0);
        CodecUtils.writeLengthEncodedInt(buf, response.getAffectedRows());
        CodecUtils.writeLengthEncodedInt(buf, response.getLastInsertId());
        if (capabilities.contains(CapabilityFlags.CLIENT_PROTOCOL_41)) {
            buf.writeShortLE((int) CodecUtils.toLong(response.getStatusFlags()))
                    .writeShortLE(response.getWarnings());

        } else if (capabilities.contains(CapabilityFlags.CLIENT_TRANSACTIONS)) {
            buf.writeShortLE((int) CodecUtils.toLong(response.getStatusFlags()));
        }
        if (capabilities.contains(CapabilityFlags.CLIENT_SESSION_TRACK)) {
            CodecUtils.writeLengthEncodedString(buf, response.getInfo(), serverCharset);
            if (response.getStatusFlags().contains(ServerStatusFlag.SESSION_STATE_CHANGED)) {
                CodecUtils.writeLengthEncodedString(buf, response.getSessionStateChanges(), serverCharset);
            }
        } else {
            if (response.getInfo() != null) {
                buf.writeCharSequence(response.getInfo(), serverCharset);
            }
        }
    }

    protected void encodeResultsetRow(Charset serverCharset, ResultsetRowPacket packet, ByteBuf buf) {
        for (String value : packet.getValues()) {
            CodecUtils.writeLengthEncodedString(buf, value, serverCharset);
        }
    }

    protected void encodeErrorResponse(EnumSet<CapabilityFlags> capabilities, ErrorResponsePacket response, ByteBuf buf) {
        buf.writeByte(0xff);
        buf.writeShortLE(response.getErrorNumber());
        if (capabilities.contains(CapabilityFlags.CLIENT_PROTOCOL_41)) {
            buf.writeByte(0x23)
                    .writeBytes(response.getSqlState());
        }
        buf.writeBytes(response.getMessage().getBytes());
    }

}
