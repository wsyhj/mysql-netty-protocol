package com.mysql.netty.protocol.server.result;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * Description:
 *
 * @Author: yuanhongjun
 * DateTime: 2021-07-20 17:32
 */
public class QueryResult extends BaseResult<List<Map<String, Object>>> implements Serializable {
    private boolean isCache = false;
    private List<QueryResultSchema> schemas;

    public QueryResult() {
    }

    public QueryResult(String taskId) {
        setTaskId(taskId);
    }

    public boolean isCache() {
        return isCache;
    }

    public void setCache(boolean cache) {
        isCache = cache;
    }

    public List<QueryResultSchema> getSchemas() {
        return schemas;
    }

    public void setSchemas(List<QueryResultSchema> schemas) {
        this.schemas = schemas;
    }
}
