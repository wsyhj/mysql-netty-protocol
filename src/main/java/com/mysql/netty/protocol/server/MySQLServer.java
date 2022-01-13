package com.mysql.netty.protocol.server;


import com.mysql.netty.protocol.server.common.*;
import com.mysql.netty.protocol.server.context.ConnectContext;
import com.mysql.netty.protocol.server.decoder.MysqlClientCommandPacketDecoder;
import com.mysql.netty.protocol.server.decoder.MysqlClientConnectionPacketDecoder;
import com.mysql.netty.protocol.server.decoder.MysqlClientPacketDecoder;
import com.mysql.netty.protocol.server.encoder.MysqlServerPacketEncoder;
import com.mysql.netty.protocol.server.packet.*;
import com.mysql.netty.protocol.server.result.QueryResult;
import com.mysql.netty.protocol.server.result.QueryResultSchema;
import com.mysql.netty.protocol.server.utils.ErrorMsgUtils;
import com.mysql.netty.protocol.server.utils.PatternUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.stream.Collectors;


/**
 *
 */
public class MySQLServer implements AutoCloseable {

    private final int port;
    private String user;
    private Channel channel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private String password;
    private Map<ChannelId, ConnectContext> connectContextMap = new HashMap<>();
    private boolean isInit = false;

    public MySQLServer(int port) {
        this.port = port;
    }

    private boolean checkAuth(HandshakeResponsePacket response) {
        //TODO 验证用户名和密码，可能需要用到公司的登录校验
        //困难点：mysql的密码被mysql自己加密了，如何获取成为最大难题
        return true;
    }

    public void init() {
        isInit = true;
    }

    public void start() {
        if (!isInit) {
            init();
        }
        int workerTheadNum = Runtime.getRuntime().availableProcessors() * 2;

        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup(Integer.valueOf(workerTheadNum));
        final ChannelFuture channelFuture = new ServerBootstrap()
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) throws Exception {
                        final ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new MysqlServerPacketEncoder());
                        pipeline.addLast(new MysqlClientConnectionPacketDecoder());
                        pipeline.addLast(new MysqlClientCommandPacketDecoder());
                        pipeline.addLast(new ServerHandler());
                    }
                })
                .bind(port).awaitUninterruptibly();
        channel = channelFuture.channel();
        System.out.println("Start MySQL server listening on port " + port);
    }

    @Override
    public void close() {
        System.out.println("Server channel close");
        channel.close();
        workerGroup.shutdownGracefully().awaitUninterruptibly();
        bossGroup.shutdownGracefully().awaitUninterruptibly();
    }

    public String getPassword() {
        return password;
    }

    public String getUser() {
        return user;
    }

    public int getPort() {
        return port;
    }

    public Map<ChannelId, ConnectContext> getConnectContextMap() {
        return connectContextMap;
    }

    private class ServerHandler extends ChannelInboundHandlerAdapter {
        private byte[] salt = new byte[21];

        public ServerHandler() {
            //最好使用全为1的
            new Random().nextBytes(salt);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            System.out.println("Server channel active " + ctx.channel().id());
            final EnumSet<CapabilityFlags> capabilities = CapabilityFlags.getImplicitCapabilities();
            CapabilityFlags.setCapabilitiesAttr(ctx.channel(), capabilities);
            ctx.writeAndFlush(HandshakePacket.builder()
                    .serverVersion("5.7.22")
                    .connectionId(0)
                    .addAuthData(salt)
                    .characterSet(MysqlCharacterSet.UTF8_BIN)
                    .addCapabilities(capabilities)
                    .addServerStatus(ServerStatusFlag.AUTO_COMMIT)
                    .authPluginName(Constants.MYSQL_NATIVE_PASSWORD)
                    .build());
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            System.out.println("Server channel {} inactive" + ctx.channel().id());
            connectContextMap.remove(ctx.channel().id());
            ctx.channel().close();
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof HandshakeResponsePacket) {
                handleHandshakeResponse(ctx, (HandshakeResponsePacket) msg);
            } else if (msg instanceof QueryCommandPacket) {
                handleQuery(ctx, (QueryCommandPacket) msg);
            } else if (msg instanceof FieldListCommandPacket) {
                handleFieldList(ctx, (FieldListCommandPacket) msg);
            } else if (msg instanceof UseDBCommandPacket) {
                handleInitDB(ctx, (UseDBCommandPacket) msg);
            } else if (msg instanceof QuitCommandPacket) {
                handleQuit(ctx, (QuitCommandPacket) msg);
            } else if (msg instanceof PingCommandPacket) {
                handlePing(ctx, (PingCommandPacket) msg);
            } else {
                System.out.println("can not analysis this message: " + msg);
                ctx.writeAndFlush(new ErrorResponsePacket(1, -1,
                        "".getBytes(), "can not analysis this command"));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            System.out.println("Server channel exceptionCaught.the info is:{}" + ErrorMsgUtils.getErrorMsg(cause));
            ctx.close();
        }

    }

    private void handleHandshakeResponse(ChannelHandlerContext ctx, HandshakeResponsePacket response) {

        this.user = response.getUsername();

        boolean checkAuth = checkAuth(response);
        if (!checkAuth) {
            ctx.writeAndFlush(new ErrorResponsePacket(1, -1,
                    "".getBytes(), "the password is error"));
            return;
        }

        ctx.pipeline().replace(MysqlClientPacketDecoder.class, "CommandPacketDecoder", new MysqlClientCommandPacketDecoder());

        ConnectContext context = new ConnectContext();
        context.setCtx(ctx);
        context.setCurrentDatabase(response.getDatabase());
        context.setProperties(response.getAttributes());
        connectContextMap.put(ctx.channel().id(), context);

        ctx.writeAndFlush(OkResponsePacket.builder()
                .addStatusFlags(ServerStatusFlag.AUTO_COMMIT)
                .sequenceId(2)
                .build());
    }

    private void handleQuery(ChannelHandlerContext ctx, QueryCommandPacket query) {
        final String queryString = query.getQuery();
        System.out.println("Received MYSQL: {}" + queryString);

        if (isSettingsQuery(ctx, queryString)) {
            ctx.writeAndFlush(OkResponsePacket.builder()
                    .sequenceId(1)
                    .addStatusFlags(ServerStatusFlag.AUTO_COMMIT)
                    .build());
            return;
        }
        if (isServerSettingsQuery(queryString)) {
            sendSettingsResponse(ctx, query);
            return;
        }

        if (isOthersType(queryString)) {
            handleOthersType(ctx, query);
            return;
        }
        handleSelectResult(ctx, query);
    }


    private void handleSelectResult(ChannelHandlerContext ctx, QueryCommandPacket query) {
        try {

            ConnectContext context = connectContextMap.get(ctx.channel().id());
            AtomicInteger sequenceId = new AtomicInteger(query.getSequenceId());

            //TODO 自定义查询操作，结果返回以及问题信息

            QueryResult queryResult = new QueryResult();

            if (!"0".equalsIgnoreCase(queryResult.getStatusCode())) {
                ctx.writeAndFlush(new ErrorResponsePacket(sequenceId.incrementAndGet(), Integer.valueOf(queryResult.getStatusCode()),
                        "".getBytes(), queryResult.getMessage()));
                return;
            }

            List<Map<String, Object>> queryResultList = queryResult.getResult();

            boolean isShowTable = PatternUtils.SHOW_TABLES_PATTERN.matcher(query.getQuery()).find();
            if (isShowTable) {
                handleShowTableResult(ctx, queryResult, sequenceId);
                return;
            }

            boolean isShowCreateTable = PatternUtils.SHOW_CREATE_TABLE_PATTERN.matcher(query.getQuery()).find();
            if (isShowCreateTable) {
                handleShowCreateTableResult(ctx, queryResult, sequenceId);
                return;
            }


            List<QueryResultSchema> schemas = queryResult.getSchemas();
            ctx.write(new ColumnCountPacket(sequenceId.incrementAndGet(), schemas.size()));

            schemas.forEach(schema -> {
                ctx.write(ColumnDefinitionPacket.builder()
                        .sequenceId(sequenceId.incrementAndGet())
                        .catalog(context.getCurrentDatabase())
                        .schema(schema.getField())
                        //TODO 根据真实查询语句表信息填充
                        .table("")
                        .orgTable("")
                        .name(schema.getField())
                        .orgName(schema.getField())
                        .columnLength(Integer.MAX_VALUE)
                        .type(ColumnType.getType(schema.getType()))
                        .addFlags(ColumnFlag.NOT_NULL)
                        .decimals(5)
                        .build());
            });

            ctx.write(new EofResponsePacket(sequenceId.incrementAndGet(), 0));

            queryResultList.forEach(map -> {
                List<String> values = map.values().stream().map(val -> {
                    return String.valueOf(val);
                }).collect(Collectors.toList());

                ctx.write(new ResultsetRowPacket(sequenceId.incrementAndGet(), values));
            });

            ctx.writeAndFlush(new EofResponsePacket(sequenceId.incrementAndGet(), 0));
        } catch (Exception e) {
            System.out.println("jdbc query is error!" + e);
            ctx.writeAndFlush(new ErrorResponsePacket(0, -1,
                    "".getBytes(), ErrorMsgUtils.getErrorMsg(e)));
        }
    }

    private boolean isOthersType(String query) {
        return "SELECT DATABASE()".equalsIgnoreCase(query) ||
                query.toLowerCase().startsWith("show variables like")
                || "SHOW ENGINES".equalsIgnoreCase(query)
                || "SHOW COLLATION".equalsIgnoreCase(query)
                || "SHOW CHARACTER SET".equalsIgnoreCase(query)
                || "SHOW STATUS".equalsIgnoreCase(query)
                || PatternUtils.DESC_TABLE_PATTERN.matcher(query).find();
    }

    private void handleOthersType(ChannelHandlerContext ctx, QueryCommandPacket packet) {
        int sequenceId = packet.getSequenceId();
        ConnectContext context = connectContextMap.get(ctx.channel().id());

        ctx.write(new ColumnCountPacket(++sequenceId, 3));
        ctx.write(ColumnDefinitionPacket.builder()
                .sequenceId(++sequenceId)
                .catalog(context.getCurrentDatabase())
                .schema(context.getCurrentDatabase())
                //TODO 根据真实查询语句表信息填充
                .table("")
                .orgTable("")
                .name("DATABASE()")
                .orgName("DATABASE()")
                .columnLength(Integer.MAX_VALUE)
                .type(ColumnType.MYSQL_TYPE_STRING)
                .addFlags(ColumnFlag.NOT_NULL)
                .decimals(5)
                .build());
        ctx.write(ColumnDefinitionPacket.builder()
                .sequenceId(++sequenceId)
                .catalog(context.getCurrentDatabase())
                .schema("Engine")
                //TODO 根据真实查询语句表信息填充
                .table("")
                .orgTable("")
                .name("Engine")
                .orgName("Engine")
                .columnLength(Integer.MAX_VALUE)
                .type(ColumnType.MYSQL_TYPE_STRING)
                .addFlags(ColumnFlag.NOT_NULL)
                .decimals(5)
                .build());
        ctx.write(ColumnDefinitionPacket.builder()
                .sequenceId(++sequenceId)
                .catalog(context.getCurrentDatabase())
                .schema("Comment")
                //TODO 根据真实查询语句表信息填充
                .table("")
                .orgTable("")
                .name("Comment")
                .orgName("Comment")
                .columnLength(Integer.MAX_VALUE)
                .type(ColumnType.MYSQL_TYPE_STRING)
                .addFlags(ColumnFlag.NOT_NULL)
                .decimals(5)
                .build());

        ctx.write(new EofResponsePacket(++sequenceId, 0));
        ctx.write(new ResultsetRowPacket(++sequenceId, context.getCurrentDatabase(), "olap", "olap engine"));
        ctx.writeAndFlush(new EofResponsePacket(++sequenceId, 0));
    }

    private boolean isServerSettingsQuery(String query) {
        query = query.toLowerCase();
        return query.contains("select") && !query.contains("from") && query.contains("@@");
    }

    private boolean isSettingsQuery(ChannelHandlerContext ctx, String query) {
        query = query.toLowerCase();
        Matcher matcher = PatternUtils.SETTINGS_PATTERN.matcher(query);
        boolean isSetting = false;
        while (matcher.find()) {
            isSetting = true;
            ConnectContext context = connectContextMap.get(ctx.channel().id());
            if ("null".equalsIgnoreCase(matcher.group(7))) {
                continue;
            }
            context.setSettingSQL(matcher.group(3), matcher.group(7));
            connectContextMap.put(ctx.channel().id(), context);
        }

        return isSetting;
    }

    private void sendSettingsResponse(ChannelHandlerContext ctx, QueryCommandPacket query) {
        final Matcher matcher = PatternUtils.SELECT_SETTINGS_PATTERN.matcher(query.getQuery());

        final List<String> values = new ArrayList<>();
        int sequenceId = query.getSequenceId();
        int i = 0;
        while (matcher.find()) {
            i++;
        }
        ctx.write(new ColumnCountPacket(++sequenceId, i));
        matcher.reset();
        while (matcher.find()) {
            String systemVariable = matcher.group(1);
            String fieldName = matcher.group(8);
            if (fieldName == null) {
                fieldName = "@@" + systemVariable;
            }
//            String fieldName = matcher.group(2);
            switch (systemVariable) {
                case "character_set_client":
                case "character_set_connection":
                case "character_set_results":
                case "character_set_server":
                case "collation_connection":
                case "performance_schema":
                case "character_set_database":
                case "collation_database":
                    ctx.write(newColumnDefinition(++sequenceId, fieldName, systemVariable, ColumnType.MYSQL_TYPE_VAR_STRING, 12));
                    values.add("utf8");
                    break;
                case "collation_server":
                    ctx.write(newColumnDefinition(++sequenceId, fieldName, systemVariable, ColumnType.MYSQL_TYPE_VAR_STRING, 21));
                    values.add("utf8_general_ci");
                    break;
                case "init_connect":
                    ctx.write(newColumnDefinition(++sequenceId, fieldName, systemVariable, ColumnType.MYSQL_TYPE_VAR_STRING, 63));
                    values.add("SET NAMES utf8");
                    break;
                case "interactive_timeout":
                case "wait_timeout":
                    ctx.write(newColumnDefinition(++sequenceId, fieldName, systemVariable, ColumnType.MYSQL_TYPE_INT24, 12));
                    values.add("28800");
                    break;
                case "language":
                    ctx.write(newColumnDefinition(++sequenceId, fieldName, systemVariable, ColumnType.MYSQL_TYPE_VAR_STRING, 12));
                    values.add("CN");
                    break;
                case "license":
                    ctx.write(newColumnDefinition(++sequenceId, fieldName, systemVariable, ColumnType.MYSQL_TYPE_VAR_STRING, 21));
                    values.add("ASLv2");
                    break;
                case "lower_case_table_names":
                    ctx.write(newColumnDefinition(++sequenceId, fieldName, systemVariable, ColumnType.MYSQL_TYPE_TINY, 2));
                    values.add("0");
                    break;
                case "max_allowed_packet":
                    ctx.write(newColumnDefinition(++sequenceId, fieldName, systemVariable, ColumnType.MYSQL_TYPE_INT24, 63));
                    values.add("4194304");
                    break;
                case "net_buffer_length":
                    ctx.write(newColumnDefinition(++sequenceId, fieldName, systemVariable, ColumnType.MYSQL_TYPE_INT24, 12));
                    values.add("16384");
                    break;
                case "net_write_timeout":
                    ctx.write(newColumnDefinition(++sequenceId, fieldName, systemVariable, ColumnType.MYSQL_TYPE_INT24, 12));
                    values.add("60");
                    break;
                case "have_query_cache":
                    ctx.write(newColumnDefinition(++sequenceId, fieldName, systemVariable, ColumnType.MYSQL_TYPE_VAR_STRING, 6));
                    values.add("YES");
                    break;
                case "sql_mode":
                    ctx.write(newColumnDefinition(++sequenceId, fieldName, systemVariable, ColumnType.MYSQL_TYPE_SET, 128));
                    values.add("ONLY_FULL_GROUP_BY,NO_AUTO_VALUE_ON_ZERO,STRICT_TRANS_TABLES,NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION");
                    break;
                case "system_time_zone":
                    ctx.write(newColumnDefinition(++sequenceId, fieldName, systemVariable, ColumnType.MYSQL_TYPE_VAR_STRING, 6));
                    values.add("UTC");
                    break;
                case "time_zone":
                    ctx.write(newColumnDefinition(++sequenceId, fieldName, systemVariable, ColumnType.MYSQL_TYPE_VAR_STRING, 12));
                    values.add("SYSTEM");
                    break;
                case "query_cache_size":
                    ctx.write(newColumnDefinition(++sequenceId, fieldName, systemVariable, ColumnType.MYSQL_TYPE_INT24, 12));
                    values.add("0");
                    break;
                case "query_cache_type":
                    ctx.write(newColumnDefinition(++sequenceId, fieldName, systemVariable, ColumnType.MYSQL_TYPE_ENUM, 12));
                    values.add("0");
                    break;
                case "tx_isolation":
                case "transaction_isolation":
                case "session.transaction_isolation":
                    ctx.write(newColumnDefinition(++sequenceId, fieldName, systemVariable, ColumnType.MYSQL_TYPE_ENUM, 12));
                    values.add("READ-COMMITTED");
                    break;
                case "SESSION.auto_increment_increment":
                case "session.auto_increment_increment":
                case "auto_increment_increment":
                case "session.autocommit":
                    ctx.write(newColumnDefinition(++sequenceId, fieldName, systemVariable, ColumnType.MYSQL_TYPE_LONG, 12));
                    values.add("1");
                    break;
                case "version_comment":
                    ctx.write(newColumnDefinition(++sequenceId, fieldName, systemVariable, ColumnType.MYSQL_TYPE_VAR_STRING, 253));
                    values.add("kolap");
                    break;
                case "transaction_read_only":
                case "session.transaction_read_only":
                    ctx.write(newColumnDefinition(++sequenceId, fieldName, systemVariable, ColumnType.MYSQL_TYPE_VAR_STRING, 253));
                    values.add("0");
                    break;
                default:
                    throw new Error("Unknown system variable " + systemVariable);
            }
        }
        ctx.write(new EofResponsePacket(++sequenceId, 0));
        ctx.write(new ResultsetRowPacket(++sequenceId, values.toArray(new String[values.size()])));
        ctx.writeAndFlush(new EofResponsePacket(++sequenceId, 0));
    }

    private ColumnDefinitionPacket newColumnDefinition(int packetSequence, String name, String orgName, ColumnType columnType, int length) {
        return ColumnDefinitionPacket.builder()
                .sequenceId(packetSequence)
                .catalog("def")
                .name(name)
                .schema(name)
                .table("")
                .orgTable("")
                .orgName(orgName)
                .type(columnType)
                .columnLength(length)
                .build();
    }

    private void handleFieldList(ChannelHandlerContext ctx, FieldListCommandPacket packet) {
        ConnectContext connectContext = connectContextMap.get(ctx.channel().id());
        connectContext.setCurrentDatabase(packet.getDatabase());
        connectContextMap.put(ctx.channel().id(), connectContext);
        int sequenceId = packet.getSequenceId();
        ctx.write(newColumnDefinition(++sequenceId, "test", "test", ColumnType.MYSQL_TYPE_VAR_STRING, 12));
        ctx.writeAndFlush(new EofResponsePacket(++sequenceId, 0));
    }

    private void handleInitDB(ChannelHandlerContext ctx, UseDBCommandPacket packet) {
        ConnectContext connectContext = connectContextMap.get(ctx.channel().id());
        connectContext.setCurrentDatabase(packet.getDatabase());
        connectContextMap.put(ctx.channel().id(), connectContext);
        int sequenceId = packet.getSequenceId();
        ctx.writeAndFlush(OkResponsePacket.builder()
                .addStatusFlags(ServerStatusFlag.SESSION_STATE_CHANGED)
                .sequenceId(++sequenceId)
                .build());
    }

    private void handleQuit(ChannelHandlerContext ctx, QuitCommandPacket packet) {
        int sequenceId = packet.getSequenceId();
        ctx.writeAndFlush(OkResponsePacket.builder()
                .addStatusFlags(ServerStatusFlag.SESSION_STATE_CHANGED)
                .sequenceId(++sequenceId)
                .build());
    }

    private void handlePing(ChannelHandlerContext ctx, PingCommandPacket packet) {
        int sequenceId = packet.getSequenceId();
        ctx.writeAndFlush(OkResponsePacket.builder()
                .addStatusFlags(ServerStatusFlag.SESSION_STATE_CHANGED)
                .sequenceId(++sequenceId)
                .build());
    }

    private void handleShowTableResult(ChannelHandlerContext ctx, QueryResult queryResult, AtomicInteger sequenceId) {
        ConnectContext context = connectContextMap.get(ctx.channel().id());

        String database = context.getCurrentDatabase();
        ctx.write(new ColumnCountPacket(sequenceId.incrementAndGet(), 2));

        ctx.write(ColumnDefinitionPacket.builder()
                .sequenceId(sequenceId.incrementAndGet())
                .catalog(database)
                .schema("Tables_in_" + database)
                //TODO 根据真实查询语句表信息填充
                .table("")
                .orgTable("")
                .name("Tables_in_" + database)
                .orgName("Tables_in_" + database)
                .columnLength(Integer.MAX_VALUE)
                .type(ColumnType.getType("string"))
                .addFlags(ColumnFlag.NOT_NULL)
                .decimals(5)
                .build());

        ctx.write(ColumnDefinitionPacket.builder()
                .sequenceId(sequenceId.incrementAndGet())
                .catalog(database)
                .schema("Tables_type")
                //TODO 根据真实查询语句表信息填充
                .table("")
                .orgTable("")
                .name("Tables_type")
                .orgName("Tables_type")
                .columnLength(Integer.MAX_VALUE)
                .type(ColumnType.getType("string"))
                .addFlags(ColumnFlag.NOT_NULL)
                .decimals(5)
                .build());

        ctx.write(new EofResponsePacket(sequenceId.incrementAndGet(), 0));

        queryResult.getResult().forEach(map -> {
            List<String> values = new ArrayList<>();
            String tableName = map.get("tableName").toString();
            values.add(tableName);
            values.add("BASE TABLE");
            ctx.write(new ResultsetRowPacket(sequenceId.incrementAndGet(), values));
        });

        ctx.writeAndFlush(new EofResponsePacket(sequenceId.incrementAndGet(), 0));
    }

    private void handleShowCreateTableResult(ChannelHandlerContext ctx, QueryResult queryResult, AtomicInteger sequenceId) {
        ConnectContext context = connectContextMap.get(ctx.channel().id());
        String database = context.getCurrentDatabase();
        ctx.write(new ColumnCountPacket(sequenceId.incrementAndGet(), 2));

        ctx.write(ColumnDefinitionPacket.builder()
                .sequenceId(sequenceId.incrementAndGet())
                .catalog(database)
                .schema("Table")
                //TODO 根据真实查询语句表信息填充
                .table("")
                .orgTable("")
                .name("Table")
                .orgName("Table")
                .columnLength(Integer.MAX_VALUE)
                .type(ColumnType.getType("string"))
                .addFlags(ColumnFlag.NOT_NULL)
                .decimals(5)
                .build());

        ctx.write(ColumnDefinitionPacket.builder()
                .sequenceId(sequenceId.incrementAndGet())
                .catalog(database)
                .schema("Create Table")
                //TODO 根据真实查询语句表信息填充
                .table("")
                .orgTable("")
                .name("Create Table")
                .orgName("Create Table")
                .columnLength(Integer.MAX_VALUE)
                .type(ColumnType.getType("string"))
                .addFlags(ColumnFlag.NOT_NULL)
                .decimals(5)
                .build());

        ctx.write(new EofResponsePacket(sequenceId.incrementAndGet(), 0));

        queryResult.getResult().forEach(map -> {
            List<String> values = map.values().stream().map(val -> {
                return String.valueOf(val);
            }).collect(Collectors.toList());
            values.add(0, "");
            ctx.write(new ResultsetRowPacket(sequenceId.incrementAndGet(), values));
        });

        ctx.writeAndFlush(new EofResponsePacket(sequenceId.incrementAndGet(), 0));
    }

    public static void main(String[] args) {
        new MySQLServer(5024).start();
    }
}
