package com.mysql.netty.protocol.server.utils;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @Author: yuanhongjun
 * DateTime: 2021-11-02 09:52
 */
public class ErrorMsgUtils implements Serializable {
    public static String getErrorMsg(Throwable thr) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        try {
            thr.printStackTrace(printWriter);
            return stringWriter.toString();
        } finally {
            if (printWriter != null) {
                printWriter.close();
            }
        }
    }
}
