package cn.greensmartplanet.gsc.common.result;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页响应体 — 列表接口返回分页数据时用这个格式
 * <p>
 * 返回示例：
 * <pre>{@code
 *   {
 *     "code": 200,
 *     "data": {
 *       "records": [{...}, {...}],   ← 当前页的数据列表
 *       "total": 100,                ← 总共多少条
 *       "page": 1,                   ← 当前第几页
 *       "pageSize": 20               ← 每页多少条
 *     }
 *   }
 * }</pre>
 *
 * @author gsc
 */
@Data
@NoArgsConstructor
public class PageResult<T> {

    /** 当前页的数据列表 */
    private List<T> records;
    /** 总记录数 */
    private long total;
    /** 当前页码 */
    private long page;
    /** 每页条数 */
    private long pageSize;

    public PageResult(List<T> records, long total, long page, long pageSize) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    /**
     * 快捷构造方法，传入 MyBatis-Plus 分页结果直接转成 PageResult
     */
    public static <T> PageResult<T> of(List<T> records, long total, long page, long pageSize) {
        return new PageResult<>(records, total, page, pageSize);
    }
}
