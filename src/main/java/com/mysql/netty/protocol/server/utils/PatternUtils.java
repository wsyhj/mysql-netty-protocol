package com.mysql.netty.protocol.server.utils;

import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @Author: yuanhongjun
 * DateTime: 2022-01-06 16:31
 */
public class PatternUtils {

    public static Pattern SHOW_PROCEDURE_PATTERN = Pattern.compile("((?i)show)(\\s+)((?i)procedure)(\\s+)((?i)status)(\\s+)(\\w+)(\\s*)");
    public static Pattern SHOW_TABLES_PATTERN = Pattern.compile("((?i)show)(\\s+)((?i)(tables" +
            "|(full(\\s+)tables((\\s+)(\\w+|!=|=|')+)+)" +
            "))(\\s*)$");
    public static Pattern SETTINGS_PATTERN = Pattern.compile("((?i)set)(\\s+)(\\w+)(\\s*)(=*)(\\s*)(\\w+)");
    public static Pattern SELECT_SETTINGS_PATTERN = Pattern.compile("@@((\\w+)((\\.)(\\w+))?)\\s*(((?i)AS)\\s(\\w+))?");
    public static Pattern SHOW_CREATE_TABLE_PATTERN = Pattern.compile("((?i)show)(\\s+)((?i)create)(\\s+)((?i)table)(\\s+)((`(\\w+)`)|(\\w+))(\\s*)");
    public static Pattern SHOW_OTHER_PATTERN = Pattern.compile("((((?i)show)(\\s+)((?i)(" +
            "engines|collation|STATUS|CHARSET|warnings" +
            "|(CHARACTER(\\s+)SET(\\s*))" +
            "|(variables(\\s+)like(\\s*)(.*))" +
            "|(procedure(\\s+)status)" +
            "|(table(\\s+)status(\\s+)like(\\s+)\\'(\\w+)\\'))))" +
            "|(((?i)select)(\\s+)((?i)database\\(\\))))" +
            "(\\s*)$");
    public static Pattern DESC_TABLE_PATTERN = Pattern.compile("((?i)desc)(\\s+)((`(\\w+)`)|(\\w+))(\\s*)");
    public static Pattern SELECT_TABLE_PATTERN = Pattern.compile("^(((?i)with)(\\s+)(\\w+)(\\s+)((?i)as)(\\s*)[(](.*)[)])" +
            "|^(((?i)select)(.*)((?i)from)(.*))");
}
