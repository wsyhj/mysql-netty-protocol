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

package com.mysql.netty.protocol.server.packet;


import com.mysql.netty.protocol.server.common.ServerStatusFlag;
import com.mysql.netty.protocol.server.packet.AbstractMySqlPacket;
import com.mysql.netty.protocol.server.packet.MysqlServerPacket;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @Author: yuanhongjun
 * DateTime: 2022-01-12 14:42
 */
public class EofResponsePacket extends AbstractMySqlPacket implements MysqlServerPacket {

    private final int warnings;
    private final Set<ServerStatusFlag> statusFlags = EnumSet.noneOf(ServerStatusFlag.class);

    public EofResponsePacket(int sequenceId, int warnings, ServerStatusFlag... flags) {
        super(sequenceId);
        this.warnings = warnings;
        statusFlags.add(ServerStatusFlag.AUTO_COMMIT);
        Collections.addAll(statusFlags, flags);
    }

    public EofResponsePacket(int sequenceId, int warnings, Collection<ServerStatusFlag> flags) {
        super(sequenceId);
        this.warnings = warnings;
        statusFlags.add(ServerStatusFlag.AUTO_COMMIT);
        statusFlags.addAll(flags);
    }

    public int getWarnings() {
        return warnings;
    }

    public Set<ServerStatusFlag> getStatusFlags() {
        return statusFlags;
    }

//	@Override
//	public int getSequenceId() {
//		return 5;
//	}
}
